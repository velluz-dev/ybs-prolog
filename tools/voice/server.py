"""
Meta MMS speech-to-text, as an HTTP service the Spring app can call.

Not part of the Maven build and not needed for the demo to run: `demo.voice.mode=recorded`
is the default and needs no model at all. This is what you start when you want the real
thing in the loop.

Why MMS rather than another Whisper endpoint: Burmese sits in Whisper's long tail, while
`facebook/mms-1b-all` ships per-language adapter weights for 1,100+ languages. Selecting
Burmese swaps an adapter rather than hoping a multilingual decoder guesses the language, and
it makes `mya` and `eng` the same model rather than two paths with different failure modes.

The contract is the multipart shape MetaSpeechTranscriber.java posts, which is deliberately
the same one a whisper.cpp server exposes:

    POST /v1/audio/transcriptions
      file=<wav>  language=mya|eng  model=facebook/mms-1b-all
    -> {"text": "...", "language": "mya", "seconds": 1.9}

Run it:
    pip install -r requirements.txt
    python server.py                    # http://localhost:9100
"""

import functools
import io
import logging
import time

import numpy as np
import soundfile as sf
import torch
import uvicorn
from fastapi import FastAPI, File, Form, UploadFile
from transformers import AutoProcessor, Wav2Vec2ForCTC

MODEL_ID = "facebook/mms-1b-all"
TARGET_SR = 16_000
DEFAULT_LANG = "mya"

logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(message)s")
log = logging.getLogger("mms")

app = FastAPI()

log.info("loading %s (first run downloads ~3.9 GB) ...", MODEL_ID)
processor = AutoProcessor.from_pretrained(MODEL_ID)
model = Wav2Vec2ForCTC.from_pretrained(MODEL_ID)
device = "cuda" if torch.cuda.is_available() else "cpu"
model.to(device).eval()
log.info("loaded on %s", device)

# Adapters are ~2.5 MB each and loading one mutates the model, so they are loaded on demand
# and the current one is remembered. Reloading the same adapter every request would add a
# second of pointless work to a call that already takes a few.
_loaded_lang = None


def _use(language: str) -> str:
    """Point the model and the tokenizer at one language's adapter."""
    global _loaded_lang
    lang = (language or DEFAULT_LANG).strip().lower()
    if lang != _loaded_lang:
        log.info("switching adapter -> %s", lang)
        processor.tokenizer.set_target_lang(lang)
        model.load_adapter(lang)
        _loaded_lang = lang
    return lang


def _read_wav(raw: bytes) -> np.ndarray:
    """Bytes to a mono float32 waveform at 16 kHz, which is all wav2vec2 accepts."""
    audio, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)                      # to mono
    if sr != TARGET_SR:
        # Linear resample. Good enough here because the samples are already 16 kHz; a
        # different rate would be a recording mistake worth noticing rather than hiding
        # behind a high-quality resampler.
        n = int(round(len(audio) * TARGET_SR / sr))
        audio = np.interp(
            np.linspace(0, len(audio), n, endpoint=False),
            np.arange(len(audio)),
            audio,
        ).astype("float32")
        log.warning("resampled %d Hz -> %d Hz", sr, TARGET_SR)
    return audio


# ---------------------------------------------------------------- lexicon ---
# Free decoding writes whatever the acoustics suggest, which for Burmese means a stop
# comes back spelled a way no lookup will match: "လှည်းတန်း" is heard as "လှည့်တမ်း".
# Fuzzy string matching papers over some of that and invents answers for the rest -
# measured, a sentence about dinner matched a stop better than a real stop did.
#
# The fix is to stop guessing from the text and go back to the audio. The set of things
# a rider can say here is not open: it is 2,080 stop names. So for the stretch of audio
# that carried the origin, score EVERY stop name against those frames with the CTC
# forward algorithm and keep the best. That is a decision about sound, not spelling, and
# it cannot return a name that is not a real stop.


def _char_frames(pred_ids, blank_id):
    """Frame index for each character the greedy path emitted.

    CTC collapses repeats and drops blanks, so the decoded string is shorter than the
    frame sequence. This rebuilds the correspondence, which is what lets a character
    range in the transcript be turned back into a frame range in the audio.
    """
    frames, prev = [], -1
    for t, k in enumerate(pred_ids):
        if k != blank_id and k != prev:
            frames.append(t)
        prev = k
    return frames


@functools.lru_cache(maxsize=8)
def _encode_lexicon(lang: str, joined: str):
    """Tokenise the candidate names once per (language, lexicon) pair.

    Cached because the list is the same on every request and tokenising 800 names on
    each call would cost more than the rescoring it feeds.
    """
    names = [n for n in (x.strip() for x in joined.split("\n")) if n]
    vocab = processor.tokenizer.get_vocab()
    encoded, kept = [], []
    for n in names:
        ids = [vocab[c] for c in n if c in vocab]
        if len(ids) >= 2:
            encoded.append(ids)
            kept.append(n)
    log.info("lexicon[%s]: %d names, %d usable", lang, len(names), len(kept))
    return kept, encoded


def _rescore(logits, f0, f1, names, encoded, blank_id, topk=3):
    """Best lexicon entries for the frames [f0, f1), by CTC score.

    Scores are length-normalised. Without that a two-character name wins every span,
    because a shorter target has fewer ways to be wrong.
    """
    f0 = max(0, f0)
    f1 = min(logits.shape[0], max(f1, f0 + 2))
    window = torch.log_softmax(logits[f0:f1], dim=-1)          # (T, V)
    T = window.shape[0]
    n = len(encoded)
    if n == 0 or T < 2:
        return []

    lp = window.unsqueeze(1).expand(T, n, window.shape[-1]).contiguous()
    flat = torch.tensor([i for ids in encoded for i in ids], dtype=torch.long)
    lens = torch.tensor([len(ids) for ids in encoded], dtype=torch.long)
    keep = lens <= T                                            # CTC cannot fit a longer target
    if not bool(keep.any()):
        return []

    with torch.no_grad():
        loss = torch.nn.functional.ctc_loss(
            lp, flat, torch.full((n,), T, dtype=torch.long), lens,
            blank=blank_id, reduction="none", zero_infinity=True)
    score = -(loss / lens.clamp(min=1))
    score[~keep] = float("-inf")

    k = min(topk, int(keep.sum()))
    best = torch.topk(score, k)
    return [{"name": names[i], "score": round(float(v), 3)}
            for v, i in zip(best.values.tolist(), best.indices.tolist())]


def _best_pair(origin_k, dest_k):
    """Pick the origin and destination together, not one after the other.

    A journey from a place to the same place is not a journey, so the two slots cannot
    both take the name they each like best when that name is the same. Scoring the pair
    lets the slot with more to lose keep its answer and the other drop to its runner-up -
    which is how "နီလာ to မာလာ" survives the origin being misheard as "မီလာ", a single
    consonant away from the destination.
    """
    if not origin_k or not dest_k:
        return origin_k, dest_k
    best, pair = None, (origin_k, dest_k)
    for i, o in enumerate(origin_k):
        for j, d in enumerate(dest_k):
            if o["name"] == d["name"]:
                continue
            total = o["score"] + d["score"]
            if best is None or total > best:
                best = total
                pair = (origin_k[i:] + origin_k[:i], dest_k[j:] + dest_k[:j])
    return pair


def _search_split(logits, frames, lo, hi, names, encoded, blank_id, step=1):
    """Find where the origin ends and the destination begins, by trying every place.

    Used when the postposition that marked the boundary was lost in recognition. The old
    behaviour cut the region in half and hoped; this scores both halves at every candidate
    cut and keeps the cut that explains the audio best. Costs one rescoring pair per
    candidate, which is why it only runs on the degraded path.
    """
    best = None
    for k in range(2, max(3, hi - 2), step):
        a = _rescore(logits, frames[lo] - 2, frames[k - 1] + 3, names, encoded, blank_id)
        b = _rescore(logits, frames[k] - 2, frames[hi - 1] + 3, names, encoded, blank_id)
        if not a or not b:
            continue
        total = a[0]["score"] + b[0]["score"]
        if best is None or total > best[0]:
            best = (total, a, b)
    return (best[1], best[2]) if best else ([], [])


@app.post("/v1/audio/transcriptions")
async def transcribe(file: UploadFile = File(...),
                     language: str = Form(DEFAULT_LANG),
                     model_name: str = Form(MODEL_ID, alias="model"),
                     lexicon: str = Form(""),
                     from_marker: str = Form(""),
                     to_marker: str = Form("")):
    started = time.time()
    lang = _use(language)
    audio = _read_wav(await file.read())

    # Silence in, nothing out. Saying so beats returning "" and letting the caller decide
    # whether the model failed or the microphone did - the repo shipped silent placeholder
    # WAVs for months and this is exactly the case that hid it.
    peak = float(np.abs(audio).max()) if len(audio) else 0.0
    if peak < 1e-4:
        log.warning("audio is silent (peak=%.6f) - nothing to transcribe", peak)
        return {"text": "", "language": lang, "silent": True,
                "seconds": round(time.time() - started, 2)}

    inputs = processor(audio, sampling_rate=TARGET_SR, return_tensors="pt").to(device)
    with torch.no_grad():
        logits = model(**inputs).logits
    pred = logits.argmax(dim=-1)[0].cpu()
    text = processor.decode(pred.numpy()).strip()

    out = {"text": text, "language": lang}

    # Constrained pass: turn the marker positions in the transcript back into frame
    # ranges, then ask the audio which stop name those frames actually were.
    if lexicon and from_marker:
        blank_id = processor.tokenizer.pad_token_id
        names, encoded = _encode_lexicon(lang, lexicon)
        frames = _char_frames(pred.tolist(), blank_id)
        raw = processor.decode(pred.numpy())          # undtrimmed: indices line up with frames

        if len(frames) == len(raw) and names:
            fm = raw.find(from_marker)
            # The FIRST "ကို" after the origin marker, not the last. "ကို" is a common
            # syllable that also turns up inside stop names, so neither choice is safe -
            # but taking the last was measured and was worse: it pushed the destination
            # span past the name and cost sample 3 its origin as well. Tried, measured,
            # reverted.
            tm = raw.find(to_marker, fm + len(from_marker)) if (to_marker and fm >= 0) else -1

            def span(a, b):
                a = max(0, min(a, len(frames) - 1))
                b = max(a + 1, min(b, len(frames)))
                # A couple of frames of slack each side: the marker itself is part of the
                # spoken word, and CTC emits a character a frame or two after it begins.
                return frames[a] - 2, (frames[b - 1] if b <= len(frames) else frames[-1]) + 3

            logit_cpu = logits[0].cpu()
            origin_k, dest_k = [], []

            if fm > 0:
                a, b = span(0, fm)
                origin_k = _rescore(logit_cpu, a, b, names, encoded, blank_id)
                if tm > fm:
                    a, b = span(fm + len(from_marker), tm)
                    dest_k = _rescore(logit_cpu, a, b, names, encoded, blank_id)
            elif to_marker and (tm := raw.find(to_marker)) > 4:
                # The postposition that marked the boundary was lost - MMS heard "မ" for
                # "မှ" - so the boundary is searched for instead of assumed.
                origin_k, dest_k = _search_split(
                    logit_cpu, frames, 0, min(tm, len(frames)), names, encoded, blank_id)

            origin_k, dest_k = _best_pair(origin_k, dest_k)
            out["constrained"] = {"origin": origin_k, "destination": dest_k}
        else:
            log.warning("frame/char mismatch (%d vs %d) - skipping constrained pass",
                        len(frames), len(raw))

    took = time.time() - started
    out["seconds"] = round(took, 2)
    log.info("[%s] %.2fs audio -> %.1fs  %r%s", lang, len(audio) / TARGET_SR, took, text,
             "  " + str(out.get("constrained", "")) if "constrained" in out else "")
    return out


@app.get("/health")
def health():
    return {"ok": True, "model": MODEL_ID, "device": device, "adapter": _loaded_lang}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=9100, log_level="warning")
