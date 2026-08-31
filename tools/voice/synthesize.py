"""
Generate the demo's voice samples with Meta MMS text-to-speech.

The repo shipped `frontend/voice/sample1-3.wav` as two seconds of digital silence. That was
fine while the voice step was a scripted playback, and useless the moment a real recogniser
was pointed at it: no model is accurate on silence, so "the model does not work" and "there
is nothing to transcribe" looked identical.

This writes real Burmese and English speech for those three queries using `facebook/mms-tts-*`
- the same model family as the recogniser, so one download serves both directions.

**These are synthetic voices, and that matters when you quote an accuracy number.** TTS output
is cleaner than a person on a bus: no background noise, no accent, no hesitation. Round-tripping
MMS-TTS through MMS-ASR measures that the pipeline is wired up and that the Burmese adapter is
doing real work. It is not a word error rate on human speech, and should never be presented as
one. Replace these with real recordings when you can get them; the filenames are all that this
script owns.

    python synthesize.py            # writes ../../frontend/voice/*.wav
"""

import pathlib
import sys

import numpy as np
import soundfile as sf
import torch
from transformers import AutoTokenizer, VitsModel

OUT = pathlib.Path(__file__).resolve().parents[2] / "frontend" / "voice"
TARGET_SR = 16_000       # what wav2vec2 wants, so no resample at recognition time

# Must match RecordedTranscriber.TRANSCRIPTS / TRANSCRIPTS_EN exactly. If you change a line
# there, change it here and regenerate - a sample whose audio and expected text disagree is
# a test that lies.
SAMPLES = {
    "mya": {
        "sample1": "လှည်းတန်းမှ ဆူးလေမြို့တော်ခန်းမကို သွားချင်တယ်",
        "sample2": "အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်",
        "sample3": "မြေနီကုန်းမှ ရွှေဂုံတိုင်ကို သွားချင်တယ်",
        "sample4": "ဘုရင့်နောင်မှ ကျောက်ကုန်းကို သွားချင်တယ်",
        "sample5": "မန္တလေးလမ်းမှ စံရိပ်ငြိမ်ကို သွားချင်တယ်",
        "sample6": "အလုံစာတိုက်မှ ပိတောက်ကွေ့ကို သွားချင်တယ်",
        "sample7": "နွားခြံမှ မာန်ပြေကို သွားချင်တယ်",
        "sample8": "ထူပါရုံမှ သဲကွင်းကို သွားချင်တယ်",
        "sample9": "နီလာမှ မာလာကို သွားချင်တယ်",
        "sample10": "ကုန်ဈေးတန်းမှ လက်မှတ်ဂိတ်ကို သွားချင်တယ်",
        "sample11": "ဦးထွန်းမြတ်မှ စံပြဈေးကွေ့ကို သွားချင်တယ်",
        "sample12": "ရဲစခန်းမှ မီးဒုံးကို သွားချင်တယ်",
    },
    "eng": {
        "sample1": "I want to go from Hledan to Sule Myodaw Khan Ma",
        "sample2": "I want to go from Insein Zay to Botahtaung Paya",
        "sample3": "I want to go from Myaynigone to Shwe Gon Daing",
        "sample4": "I want to go from Bayint Naung to Kyauk Kone",
        "sample5": "I want to go from Mandalay Lan to San Yeik Nyein",
        "sample6": "I want to go from Ahlone Sar Taik to Padauk Kwae",
        "sample7": "I want to go from Nwar Chan to Man Pyae",
        "sample8": "I want to go from Htu Par Yon to Thal Kwin",
        "sample9": "I want to go from Nilar to Marlar",
        "sample10": "I want to go from Kone Zay Tan to Lat Hmat Gate",
        "sample11": "I want to go from U Tun Myat to Sanpya Zay Kwae",
        "sample12": "I want to go from Ye Sa Khan to Mee Don",
    },
}

# Burmese keeps the bare name so it stays the default the app asks for; English gets the
# .en suffix MetaSpeechTranscriber looks for first.
SUFFIX = {"mya": "", "eng": ".en"}


def resample(x: np.ndarray, src: int, dst: int) -> np.ndarray:
    if src == dst:
        return x
    n = int(round(len(x) * dst / src))
    return np.interp(np.linspace(0, len(x), n, endpoint=False),
                     np.arange(len(x)), x).astype("float32")


def synth(lang: str) -> None:
    repo = f"facebook/mms-tts-{lang}"
    print(f"[{lang}] loading {repo} ...", flush=True)
    tok = AutoTokenizer.from_pretrained(repo)
    model = VitsModel.from_pretrained(repo).eval()
    sr = model.config.sampling_rate

    for key, text in SAMPLES[lang].items():
        inputs = tok(text, return_tensors="pt")
        with torch.no_grad():
            wave = model(**inputs).waveform[0].cpu().numpy().astype("float32")

        wave = resample(wave, sr, TARGET_SR)
        peak = float(np.abs(wave).max())
        if peak < 1e-4:
            print(f"[{lang}] {key}: SILENT output, refusing to write", file=sys.stderr)
            continue
        wave = wave / peak * 0.95          # normalise, so every sample lands at the same level

        path = OUT / f"{key}{SUFFIX[lang]}.wav"
        sf.write(path, wave, TARGET_SR, subtype="PCM_16")
        print(f"[{lang}] {path.name:18} {len(wave)/TARGET_SR:5.2f}s  {text[:44]}", flush=True)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    # Optional language filter, so Burmese can be regenerated without waiting on the
    # English weights: `python synthesize.py mya`
    wanted = [a for a in sys.argv[1:] if a in SAMPLES] or ["mya", "eng"]
    for lang in wanted:
        synth(lang)
    print("done")
