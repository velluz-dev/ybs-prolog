# Voice — speech to text, in Burmese and English

The voice step is `audio → transcript → entities → search`. Only the first arrow has a
choice of implementation; the rest is the same code whichever path ran.

| Mode | What runs | Needs |
|---|---|---|
| `recorded` **(default)** | no model — three known transcripts per language | nothing |
| `mms` | **Meta MMS**, `facebook/mms-1b-all` | a self-hosted endpoint |
| `whisper` | whisper.cpp / faster-whisper | a self-hosted endpoint |

Set it in `application.properties` as `demo.voice.mode`, or per request with
`POST /api/voice/query?mode=mms`. **Both model paths fall back to `recorded` the moment the
endpoint is unreachable**, and the response's `mode` field says so — `mms:mya (fell back to
recorded)`. A live demo must not hard-fail because an optional GPU box is off, and a silent
fallback would be worse than a loud one: it would let someone claim a model ran when it did
not.

## Why MMS and not just Whisper

Whisper's Burmese is weak — Burmese sits far down its training distribution. Meta's
[MMS](https://ai.meta.com/blog/multilingual-model-speech-recognition/) was built for exactly
that tail: `mms-1b-all` carries **per-language adapter weights for 1,100+ languages**, so
selecting Burmese swaps an adapter rather than hoping a multilingual decoder guesses the
language from the audio. It also means the two languages this app offers are the *same*
model with a different adapter, instead of a Burmese path and an English path with different
failure modes.

MMS wants **ISO 639-3** codes, not the two-letter ones the UI uses:

| UI language | MMS adapter |
|---|---|
| `my` | `mya` |
| `en` | `eng` |

`MetaSpeechTranscriber.adapterFor` is the only place that mapping lives. An unrecognised
code becomes `mya`, because the recordings are Burmese and guessing `eng` would transcribe
Burmese audio with an English adapter and return confident nonsense rather than failing.

## The contract

Deliberately the same multipart shape the Whisper path uses, so one small process can serve
both routes:

```
POST {demo.voice.meta.url}
  multipart/form-data:  file=<wav>  language=mya|eng  model=facebook/mms-1b-all
200 -> {"text": "..."}
```

## Running the real model

It lives in [`tools/voice/`](../tools/voice/) — a real, runnable service rather than a
snippet, kept out of the Maven build because the demo must not depend on it.

```bash
cd tools/voice
pip install -r requirements.txt
python synthesize.py       # writes real speech into ../../frontend/voice/
python server.py           # :9100 — first run downloads ~3.9 GB
python check_accuracy.py   # round-trips the samples and prints a CER
```

Then set `demo.voice.mode=mms`, or pass `&mode=mms` on one request without restarting.

| File | What it is |
|---|---|
| `server.py` | the ASR service; loads the adapter on demand and caches which one is loaded |
| `synthesize.py` | regenerates `frontend/voice/*.wav` from the sample sentences |
| `check_accuracy.py` | transcribes them back, reports character error rate, then checks the app still resolves both stops |

## The samples were two seconds of silence

Worth knowing, because it hid for a long time and would hide again.

`frontend/voice/sample1-3.wav` shipped as **digital silence** — every sample zero. Harmless
while the voice step was scripted playback of a known transcript, and useless the moment a
real recogniser was pointed at them: an empty transcript from a working model and an empty
transcript from a broken one look identical, and the client's fallback made both of them look
like success.

Two things came out of that:

- `synthesize.py` writes real speech for the three queries using `facebook/mms-tts-mya` and
  `facebook/mms-tts-eng` — the same model family as the recogniser, so one download serves
  both directions.
- `server.py` checks the waveform's peak and answers `{"text": "", "silent": true}` rather
  than an empty string, so "nothing to transcribe" can never again be mistaken for "the model
  failed".

**These are synthetic voices.** TTS output has no background noise, no accent and no
hesitation, so these numbers prove the pipeline is wired up and the Burmese adapter is doing
real work — they are **not** a word error rate on human speech and must never be quoted as
one. Replace them with real recordings when you can get them; the filenames are the only
thing `synthesize.py` owns.

## What it actually scores

Twelve queries through `facebook/mms-1b-all` and back (`check_accuracy.py`, CPU):

| | free decoding | **lexicon-constrained** |
|---|---|---|
| both stops correct | 5 / 12 | **10 / 12** |
| filled in, but wrong | 4 / 12 | **2 / 12** |
| no answer given | 3 / 12 | **0 / 12** |
| character error rate | 26 – 40% | unchanged |

**The character error rate did not move, and that is the point.** The transcripts are just
as wrong as before — "လှည်းတန်း" still comes back as "လှည့်တမ်း". What changed is that the
answer no longer depends on the spelling.

### Constraining the decoder to the stop list

Free decoding writes whatever the acoustics suggest and leaves the app to recognise it. At a
30% character error rate that is a losing game, and the losses are the expensive kind: a
sentence about *dinner* matched a stop name better (0.600) than a real stop match did
(0.556), so no similarity threshold could keep one and reject the other.

The fix is to stop asking the question in text. The set of things a rider can name here is
not open — it is 2,080 stops. So:

1. Decode freely once, and keep the frame each character was emitted on. CTC collapses
   repeats and drops blanks, so this correspondence has to be rebuilt, and it is what lets a
   character range in the transcript become a **frame range in the audio**.
2. Find the postpositions မှ and ကို in that transcript. They delimit the origin and the
   destination.
3. For each span, score **every stop name** against those frames with the CTC forward
   algorithm, length-normalised, and keep the best.

That is a decision about sound rather than spelling, and it structurally cannot return a
place that does not exist. 824 distinct names scored per span, batched, costs a few hundred
milliseconds on CPU.

Two details that matter:

- **Length normalisation is not optional.** Without it a two-character name wins every span,
  because a shorter target has fewer ways to be wrong.
- **A lost postposition is handled, crudely.** MMS heard "မ" for "မှ" on one sample, taking
  the boundary between the two stops with it. Everything before ကို still holds both names in
  order, so it is split down the middle rather than abandoned — that recovered sample 3's
  origin and still gets its destination wrong.

### What is still wrong

- **sample 3's destination** — the split-in-the-middle fallback puts the boundary in the
  wrong place. A proper fix scores sliding windows instead of guessing a midpoint.
- **sample 9's origin** — "နီလာ" heard as "မီလာ", scored closer to "မာလာ". Four characters,
  one consonant apart, no context to disambiguate. A bigram over stop pairs that actually
  connect would settle it.
- **English is unconstrained.** The same trick applies, but the English adapter already reads
  the Latin names well enough for the ordinary match, and shipping a second lexicon without
  measuring it would be a change with no evidence behind it.

### Why the threshold is 0.66 and not lower

Approximate string matching is now the **fallback** - it covers English, the recorded
path, and any span the constrained pass could not locate. Where it still runs, it is capped
at 0.66 similarity, and the cap costs real matches:

- "ဟိုတယ်ကြီးမှာ ညစာ စားချင်တယ်" — *I want dinner at the big hotel*, naming no stop at all —
  has a nearest stop name scoring **0.600**.
- A genuine match of a recognised "လှည့်တမ်" to the stop "လှည်းတန်း" scored **0.556**.

The false positive scores **higher than the true positive**, so no threshold separates them
and the only choice is which error to prefer. Two further rules follow:

- **No grammatical marker, no guess.** Splitting a sentence in half and matching each side
  turned the dinner sentence into a journey. A sentence that cannot be parsed stays unparsed.
- **Names shorter than four characters are never matched approximately.** "ဘုရ" scored a
  perfect 1.0 against a transcript that did not contain that stop.

recordings — not a lower number here.

## Entity extraction, in two languages

`EntityExtractor` decides which pass to run **from the transcript itself** — a string
containing Myanmar code points (U+1000–U+109F) is Burmese. It is not told the language,
which keeps it right even when a model returns Burmese for an English request.

**Burmese** marks direction *after* the name, as a postposition. Burmese is written without
spaces, so tokenising is not an option; the scan finds each stop name anywhere in the string
and reads the characters that follow it:

```
လှည်းတန်းမှ  ဆူးလေမြို့တော်ခန်းမကို  သွားချင်တယ်
        └ မှ = from            └ ကို = to
```

**English** marks it *before*, as a preposition, and the township has to come off — the data
calls a stop `Hledan (Kamayut)` because four stops share the name *Nat Sin Lan* and the
township is what separates them, but nobody says the bracket out loud:

```
I want to go from Hledan to Sule Myodaw Khan Ma
                 └ from        └ to
```

Two consequences worth saying out loud in a viva:

- **The English pass loses the disambiguation the brackets carried.** A spoken English query
  resolves to the first stop of that name. That is honest rather than fixable by string
  matching — the speaker did not say which Hledan they meant.
- **Every occurrence of the marker is checked, not the first.** "I want **to** go from Hledan
  **to** Sule" has a `to` before *go*; stopping at the first match would parse the wrong
  thing. And the name has to end on a word boundary, or `to Shwe Gon` would match inside
  `to Shwe Gon Daing` and take the shorter stop.

## Recordings

`frontend/voice/` holds the sample audio: `sample1.wav`…`sample3.wav` in Burmese and
`sample1.en.wav`…`sample3.en.wav` in English. `MetaSpeechTranscriber` looks for the `.en` file
first when the language is English and falls back to the Burmese one if it is missing, so a
language can be added without touching the Java.

**One trap when you regenerate them.** The Java reads
`ClassPathResource("static/voice/…")`, which is the copy `pom.xml` makes from `../frontend`
at build time — not the file you just wrote. The browser sees the new audio immediately
(the frontend is served from disk) while the recogniser is still being handed the old one.
Run `./mvnw compile` or restart after `synthesize.py`.

The transcripts in `RecordedTranscriber` must name **stops that actually exist** — the
extractor matches against seeded stop names, and two Burmese samples silently stopped
resolving when the demo moved to the real YBS export. If you reseed the database, re-check
them.
