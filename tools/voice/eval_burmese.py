"""
Measure Burmese recognition on an independent set of sentences.

`check_accuracy.py` scores the twelve queries the demo ships, which were written to
exercise the planner - short, same shape every time, stop names in fixed positions.
Scoring a recogniser on the sentences it was tuned around flatters it.

This runs the same round trip over any JSONL with a `text` field, so a corpus written
for something else entirely becomes a held-out test set. It was built against
`training/burmese-patient-situations.jsonl` from the UIT hackathon project: 22 ambulance
call notes, mean 64 characters, full of numerals and clinical vocabulary this system has
never seen.

    python eval_burmese.py --jsonl "path/to/burmese-patient-situations.jsonl"

**What this can and cannot tell you.** It measures the recogniser on synthetic speech, so
it is a fair comparison BETWEEN configurations - adapters, thresholds, sample rates - and
not a word error rate on human callers. And it cannot train anything: fine-tuning MMS needs
recordings paired with their transcripts, and a transcript-only corpus has no audio to
learn from.
"""

import argparse
import json
import pathlib
import statistics
import sys
import tempfile
import unicodedata

import numpy as np
import requests
import soundfile as sf
import torch
from transformers import AutoTokenizer, VitsModel

TARGET_SR = 16_000


def normalise(s: str) -> str:
    """NFC and collapsed spaces. Burmese can encode one syllable two ways, and comparing
    raw code points would invent errors that are not there."""
    return " ".join(unicodedata.normalize("NFC", s).strip().lower().split())


def cer(expected: str, got: str) -> float:
    a, b = normalise(expected).replace(" ", ""), normalise(got).replace(" ", "")
    if not a:
        return 0.0 if not b else 1.0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1] / len(a)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jsonl", required=True, help="one record per line, with a 'text' field")
    ap.add_argument("--field", default="text")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--stt", default="http://localhost:9100/v1/audio/transcriptions")
    ap.add_argument("--lang", default="mya")
    ap.add_argument("--keep", default="", help="directory to keep the synthesised wavs in")
    args = ap.parse_args()

    rows = [json.loads(l) for l in open(args.jsonl, encoding="utf-8") if l.strip()]
    texts = [r[args.field] for r in rows if r.get(args.field)]
    if args.limit:
        texts = texts[:args.limit]
    if not texts:
        print(f"no '{args.field}' field found in {args.jsonl}", file=sys.stderr)
        return 1

    repo = f"facebook/mms-tts-{args.lang}"
    print(f"loading {repo} to voice {len(texts)} sentences ...", flush=True)
    tok = AutoTokenizer.from_pretrained(repo)
    tts = VitsModel.from_pretrained(repo).eval()
    sr = tts.config.sampling_rate

    keep = pathlib.Path(args.keep) if args.keep else None
    if keep:
        keep.mkdir(parents=True, exist_ok=True)
    scratch_dir = tempfile.TemporaryDirectory(prefix="ybs-eval-")
    scratch = pathlib.Path(scratch_dir.name)

    print(f"\n{'#':>3}  {'CER':>7}  {'chars':>5}  transcript")
    print("-" * 96)
    scores = []

    for i, text in enumerate(texts, 1):
        with torch.no_grad():
            wave = tts(**tok(text, return_tensors="pt")).waveform[0].cpu().numpy().astype("float32")
        if sr != TARGET_SR:
            n = int(round(len(wave) * TARGET_SR / sr))
            wave = np.interp(np.linspace(0, len(wave), n, endpoint=False),
                             np.arange(len(wave)), wave).astype("float32")
        peak = float(np.abs(wave).max())
        if peak > 0:
            wave = wave / peak * 0.95

        # mkstemp hands back an OPEN descriptor, and Windows will not unlink a file that
        # something still holds - so the scratch file is just a path inside a directory
        # this process owns, and gets removed at the end.
        path = (keep / f"{i:03d}.wav") if keep else (scratch / f"{i:03d}.wav")
        sf.write(path, wave, TARGET_SR, subtype="PCM_16")

        try:
            with open(path, "rb") as fh:
                r = requests.post(args.stt, files={"file": (path.name, fh, "audio/wav")},
                                  data={"language": args.lang}, timeout=300)
            r.raise_for_status()
            got = r.json().get("text", "")
        except Exception as e:
            print(f"  recogniser unreachable: {e}", file=sys.stderr)
            return 1
        finally:
            if not keep:
                path.unlink(missing_ok=True)


        rate = cer(text, got)
        scores.append(rate)
        flag = "  <-- poor" if rate > 0.45 else ""
        print(f"{i:3d}  {rate:6.1%}  {len(text):5d}  {got[:64]}{flag}", flush=True)

    print("-" * 96)
    scores.sort()
    print(f"sentences   {len(scores)}")
    print(f"mean CER    {statistics.mean(scores):.1%}")
    print(f"median CER  {statistics.median(scores):.1%}")
    print(f"best/worst  {scores[0]:.1%} / {scores[-1]:.1%}")
    print(f"under 30%   {sum(1 for s in scores if s < 0.30)}/{len(scores)}")
    print("\nSynthetic speech: comparable between runs, not a WER on human callers.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
