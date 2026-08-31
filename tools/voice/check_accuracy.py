"""
Round-trip check: does the recogniser actually read the samples back?

Posts each WAV in frontend/voice/ to the running MMS server and compares the transcript
with the sentence it was synthesised from. Then asks the Spring app itself, so the answer
covers the thing that matters to the demo - not "was the text close" but "did the two stops
come out".

    python server.py &          # in another terminal
    python check_accuracy.py    # optionally: --app http://localhost:8082

**On the number this prints.** The audio is MMS-TTS output, so it is clean: no background
noise, no accent, no hesitation, no bus. A low error rate here means the pipeline is wired
up and the Burmese adapter is doing real work. It is NOT a word error rate on human speech
and must not be quoted as one.

Burmese is written without spaces, so the character error rate is the honest metric; a word
error rate would be measuring the tokeniser, not the recogniser.
"""

import argparse
import pathlib
import sys
import unicodedata

import requests

VOICE = pathlib.Path(__file__).resolve().parents[2] / "frontend" / "voice"

EXPECTED = {
    "sample1.wav": ("mya", "လှည်းတန်းမှ ဆူးလေမြို့တော်ခန်းမကို သွားချင်တယ်", "လှည်းတန်း", "ဆူးလေမြို့တော်ခန်းမ"),
    "sample2.wav": ("mya", "အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်", "အင်းစိန်ဈေး", "ဗိုလ်တထောင်ဘုရား"),
    "sample3.wav": ("mya", "မြေနီကုန်းမှ ရွှေဂုံတိုင်ကို သွားချင်တယ်", "မြေနီကုန်း", "ရွှေဂုံတိုင်"),
    "sample4.wav": ("mya", "ဘုရင့်နောင်မှ ကျောက်ကုန်းကို သွားချင်တယ်", "ဘုရင့်နောင်", "ကျောက်ကုန်း"),
    "sample5.wav": ("mya", "မန္တလေးလမ်းမှ စံရိပ်ငြိမ်ကို သွားချင်တယ်", "မန္တလေးလမ်း", "စံရိပ်ငြိမ်"),
    "sample6.wav": ("mya", "အလုံစာတိုက်မှ ပိတောက်ကွေ့ကို သွားချင်တယ်", "အလုံစာတိုက်", "ပိတောက်ကွေ့"),
    "sample7.wav": ("mya", "နွားခြံမှ မာန်ပြေကို သွားချင်တယ်", "နွားခြံ", "မာန်ပြေ"),
    "sample8.wav": ("mya", "ထူပါရုံမှ သဲကွင်းကို သွားချင်တယ်", "ထူပါရုံ", "သဲကွင်း"),
    "sample9.wav": ("mya", "နီလာမှ မာလာကို သွားချင်တယ်", "နီလာ", "မာလာ"),
    "sample10.wav": ("mya", "ကုန်ဈေးတန်းမှ လက်မှတ်ဂိတ်ကို သွားချင်တယ်", "ကုန်ဈေးတန်း", "လက်မှတ်ဂိတ်"),
    "sample11.wav": ("mya", "ဦးထွန်းမြတ်မှ စံပြဈေးကွေ့ကို သွားချင်တယ်", "ဦးထွန်းမြတ်", "စံပြဈေးကွေ့"),
    "sample12.wav": ("mya", "ရဲစခန်းမှ မီးဒုံးကို သွားချင်တယ်", "ရဲစခန်း", "မီးဒုံး"),
    "sample1.en.wav": ("eng", "I want to go from Hledan to Sule Myodaw Khan Ma", "Hledan", "Sule Myodaw Khan Ma"),
    "sample2.en.wav": ("eng", "I want to go from Insein Zay to Botahtaung Paya", "Insein Zay", "Botahtaung Paya"),
    "sample3.en.wav": ("eng", "I want to go from Myaynigone to Shwe Gon Daing", "Myaynigone", "Shwe Gon Daing"),
    "sample4.en.wav": ("eng", "I want to go from Bayint Naung to Kyauk Kone", "Bayint Naung", "Kyauk Kone"),
    "sample5.en.wav": ("eng", "I want to go from Mandalay Lan to San Yeik Nyein", "Mandalay Lan", "San Yeik Nyein"),
    "sample6.en.wav": ("eng", "I want to go from Ahlone Sar Taik to Padauk Kwae", "Ahlone Sar Taik", "Padauk Kwae"),
    "sample7.en.wav": ("eng", "I want to go from Nwar Chan to Man Pyae", "Nwar Chan", "Man Pyae"),
    "sample8.en.wav": ("eng", "I want to go from Htu Par Yon to Thal Kwin", "Htu Par Yon", "Thal Kwin"),
    "sample9.en.wav": ("eng", "I want to go from Nilar to Marlar", "Nilar", "Marlar"),
    "sample10.en.wav": ("eng", "I want to go from Kone Zay Tan to Lat Hmat Gate", "Kone Zay Tan", "Lat Hmat Gate"),
    "sample11.en.wav": ("eng", "I want to go from U Tun Myat to Sanpya Zay Kwae", "U Tun Myat", "Sanpya Zay Kwae"),
    "sample12.en.wav": ("eng", "I want to go from Ye Sa Khan to Mee Don", "Ye Sa Khan", "Mee Don"),
}


def normalise(s: str) -> str:
    """NFC, lowercased, spaces collapsed. Burmese needs the NFC: the same syllable can be
    encoded two ways and comparing the raw code points would invent errors that are not
    there."""
    s = unicodedata.normalize("NFC", s).strip().lower()
    return " ".join(s.split())


def cer(expected: str, got: str) -> float:
    """Levenshtein over characters, divided by the reference length."""
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
    ap.add_argument("--stt", default="http://localhost:9100/v1/audio/transcriptions")
    ap.add_argument("--app", default="http://localhost:8082")
    args = ap.parse_args()

    print(f"{'file':18} {'lang':5} {'CER':>7}  transcript")
    print("-" * 78)
    worst, checked = 0.0, 0

    for name, (lang, expected, _wantO, _wantD) in EXPECTED.items():
        path = VOICE / name
        if not path.exists():
            print(f"{name:18} {lang:5} {'-':>7}  (missing - run synthesize.py)")
            continue
        with open(path, "rb") as fh:
            try:
                r = requests.post(args.stt, files={"file": (name, fh, "audio/wav")},
                                  data={"language": lang}, timeout=180)
                r.raise_for_status()
            except Exception as e:
                print(f"{name:18} {lang:5} {'-':>7}  server unreachable: {e}")
                return 1
        body = r.json()
        got = body.get("text", "")
        rate = cer(expected, got)
        worst = max(worst, rate)
        checked += 1
        flag = "" if rate < 0.35 else "   <-- poor"
        print(f"{name:18} {lang:5} {rate:6.1%}  {got!r}{flag}")

    if not checked:
        print("\nNothing checked. Run synthesize.py first.")
        return 1

    # The number the demo actually depends on: not the string, but whether the two stops
    # came out. A transcript can be imperfect and still resolve both endpoints.
    print("\nThrough the app, with entity extraction:")
    # The app protects every POST with CSRF, so a bare requests.post gets 403. One GET to
    # pick the token out of the cookie is all it takes - the browser does the same thing.
    session = requests.Session()
    session.get(f"{args.app}/api/auth/me", timeout=30)
    session.headers["X-XSRF-TOKEN"] = session.cookies.get("XSRF-TOKEN", "")

    resolved = 0
    wrong = 0
    keys = [k[:-4] for k in EXPECTED if not k.endswith(".en.wav")]
    for sample in keys:
        lang = "my"
        try:
            r = session.post(f"{args.app}/api/voice/query",
                             params={"sample": sample, "lang": lang, "mode": "mms"}, timeout=180)
            d = r.json()
            # "understood" only means both slots got filled. Filling them with the WRONG
            # stops is the failure that actually hurts - it routes someone across Yangon
            # with confidence - so correctness is scored against the expected names, and
            # a wrong answer is counted separately from a missing one.
            wantO, wantD = EXPECTED[sample + ".wav"][2], EXPECTED[sample + ".wav"][3]
            gotO, gotD = d.get("originName"), d.get("destName")
            correct = (gotO == wantO and gotD == wantD)
            filled = bool(d.get("understood"))
            ok = correct
            resolved += correct
            if filled and not correct:
                wrong += 1
            # ALWAYS print the mode. Dropping it once hid a broken client behind the
            # recorded fallback and turned a fallback into a headline accuracy number.
            mode = d.get('mode', '?')
            warn = "  <-- FELL BACK, not the model" if 'fell back' in str(mode) else ""
            print(f"  {'OK  ' if ok else 'MISS'} {sample:9} [{mode}] "
                  f"origin={gotO}  dest={gotD}{warn}"
                  + ("" if correct else f"   want {wantO} -> {wantD}"))
        except Exception as e:
            print(f"  ??  {sample}: app unreachable: {e}")

    print(f"\n  resolved {resolved}/{len(keys)} Burmese queries into two stops")
    print(f"\nworst CER {worst:.1%} over {checked} clip(s).")
    print("Synthetic voices - see the docstring before quoting this anywhere.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
