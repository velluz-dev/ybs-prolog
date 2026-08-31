# The real voice model

Optional. The demo's default is `demo.voice.mode=recorded`, which runs no model and needs no
Python — that is what makes the voice step safe to show on a stage with no wifi. Everything
here is for putting Meta's **MMS** in the loop for real.

## Why MMS

Burmese sits in Whisper's long tail. `facebook/mms-1b-all` ships **per-language adapter
weights for 1,100+ languages**, Burmese among them, so choosing Burmese swaps an adapter
rather than hoping a multilingual decoder guesses the language from the audio. It also makes
`mya` and `eng` the same model with a different adapter, instead of two paths with two
different failure modes.

MMS wants ISO 639-3: `mya`, not `my`. `MetaSpeechTranscriber.adapterFor` is the only place in
the Java that mapping lives.

## Setup

```bash
pip install -r requirements.txt
python synthesize.py      # writes real speech into ../../frontend/voice/
python server.py          # http://localhost:9100, first run downloads ~3.9 GB
```

Then in `backend/src/main/resources/application.properties`:

```properties
demo.voice.mode=mms
```

or per request, without a restart: `POST /api/voice/query?sample=sample1&lang=my&mode=mms`.

If the server is not running, **nothing breaks**: the Java client falls back to the recorded
transcript and the response's `mode` says `mms:mya (fell back to recorded)`. Show that string
verbatim — it is what stops anyone claiming a model ran when it did not.

## `synthesize.py` — and the thing it fixes

`frontend/voice/sample1-3.wav` shipped as **two seconds of digital silence**. That was
harmless while the voice step was scripted playback, and useless the moment a real recogniser
was pointed at it: "the model is broken" and "there is nothing to transcribe" look identical.

`synthesize.py` writes real speech for those three queries with `facebook/mms-tts-mya` and
`facebook/mms-tts-eng` — the same model family as the recogniser, so one download serves both
directions.

**They are synthetic voices.** TTS output has no background noise, no accent and no
hesitation, so round-tripping MMS-TTS through MMS-ASR proves the pipeline is wired up and the
Burmese adapter is doing real work. It is **not** a word error rate on human speech and must
never be quoted as one. Replace them with real recordings when you can get them — the
filenames are the only thing this script owns.

## Files

| File | What it is |
|---|---|
| `server.py` | the ASR service the Java client posts to |
| `synthesize.py` | regenerates `frontend/voice/*.wav` from the sample sentences |
| `requirements.txt` | only needed for the two scripts above |
