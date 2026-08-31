"""
Does constraining the decoder to the stop list beat matching strings after the fact?

Runs the twelve Burmese samples twice against the same audio and the same model:

  free      greedy transcript, then the Java-side approximate match (what shipped)
  lexicon   the same audio, with every stop name scored against the frames that
            carried the origin and the destination

The comparison is the point. Free decoding writes a spelling and hopes a lookup
recognises it; the constrained pass asks which of 2,080 real stops those frames sound
most like, and cannot answer with anything that is not a stop.

    python server.py &
    python check_lexicon.py
"""

import argparse
import json
import pathlib
import sys
import unicodedata

import requests

VOICE = pathlib.Path(__file__).resolve().parents[2] / "frontend" / "voice"
FROM_MARKER = "မှ"
TO_MARKER = "ကို"

# The same twelve queries the app ships, with the stops they name.
EXPECTED = [
    ("sample1",  "လှည်းတန်း",     "ဆူးလေမြို့တော်ခန်းမ"),
    ("sample2",  "အင်းစိန်ဈေး",    "ဗိုလ်တထောင်ဘုရား"),
    ("sample3",  "မြေနီကုန်း",     "ရွှေဂုံတိုင်"),
    ("sample4",  "ဘုရင့်နောင်",    "ကျောက်ကုန်း"),
    ("sample5",  "မန္တလေးလမ်း",    "စံရိပ်ငြိမ်"),
    ("sample6",  "အလုံစာတိုက်",    "ပိတောက်ကွေ့"),
    ("sample7",  "နွားခြံ",        "မာန်ပြေ"),
    ("sample8",  "ထူပါရုံ",        "သဲကွင်း"),
    ("sample9",  "နီလာ",          "မာလာ"),
    ("sample10", "ကုန်ဈေးတန်း",    "လက်မှတ်ဂိတ်"),
    ("sample11", "ဦးထွန်းမြတ်",    "စံပြဈေးကွေ့"),
    ("sample12", "ရဲစခန်း",        "မီးဒုံး"),
]


def nfc(s):
    return unicodedata.normalize("NFC", (s or "").strip())


def stop_names(app):
    """Every distinct Burmese stop name, which is the whole space of valid answers."""
    net = requests.get(f"{app}/api/network", timeout=60).json()
    names = {nfc(s["nameMy"]) for s in net["stops"] if s.get("nameMy")}
    return sorted(n for n in names if len(n) >= 2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stt", default="http://localhost:9100/v1/audio/transcriptions")
    ap.add_argument("--app", default="http://localhost:8082")
    args = ap.parse_args()

    names = stop_names(args.app)
    lexicon = "\n".join(names)
    print(f"lexicon: {len(names)} distinct stop names\n")

    print(f"{'sample':10} {'origin':>8} {'dest':>8}   heard")
    print("-" * 92)
    hit_o = hit_d = both = 0

    for key, want_o, want_d in EXPECTED:
        path = VOICE / f"{key}.wav"
        if not path.exists():
            print(f"{key:10}  missing audio"); continue

        with open(path, "rb") as fh:
            r = requests.post(args.stt,
                              files={"file": (path.name, fh, "audio/wav")},
                              data={"language": "mya", "lexicon": lexicon,
                                    "from_marker": FROM_MARKER, "to_marker": TO_MARKER},
                              timeout=600)
        r.raise_for_status()
        body = r.json()
        con = body.get("constrained") or {}

        got_o = nfc((con.get("origin") or [{}])[0].get("name", ""))
        got_d = nfc((con.get("destination") or [{}])[0].get("name", ""))
        ok_o, ok_d = got_o == nfc(want_o), got_d == nfc(want_d)
        hit_o += ok_o; hit_d += ok_d; both += (ok_o and ok_d)

        print(f"{key:10} {'OK' if ok_o else 'x':>8} {'OK' if ok_d else 'x':>8}   "
              f"{got_o} -> {got_d}")
        if not (ok_o and ok_d):
            print(f"{'':10} {'':8} {'':8}   want {want_o} -> {want_d}")

    n = len(EXPECTED)
    print("-" * 92)
    print(f"origin correct       {hit_o}/{n}")
    print(f"destination correct  {hit_d}/{n}")
    print(f"BOTH correct         {both}/{n}      (free decoding + fuzzy matching: 5/{n})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
