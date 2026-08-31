package com.ybs.demo;

import com.ybs.demo.voice.EntityExtractor;
import com.ybs.demo.voice.MetaSpeechTranscriber;
import com.ybs.demo.voice.RecordedTranscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The voice path in both languages.
 *
 * <p>The interesting half is {@link EntityExtractor}: the same two stops have to come back
 * out of a Burmese transcript, where the direction marker follows the name, and an English
 * one, where it precedes it. The other half is the discipline the model clients are held
 * to - a self-hosted endpoint that is not running must degrade to the recorded transcript
 * and say so, never hard-fail and never lie about which path ran.
 */
@SpringBootTest(properties = {
        // Point the MMS client at a port nothing listens on. Several tests below assert what
        // happens when the recogniser is UNREACHABLE, and they were passing only because no
        // server happened to be running - the moment tools/voice/server.py was started on
        // this machine they failed. A test whose result depends on what else the developer
        // has open is not a test.
        "demo.voice.meta.url=http://127.0.0.1:1/v1/audio/transcriptions",
        "demo.voice.meta.timeout-ms=250"
})
class VoiceTest {

    @Autowired EntityExtractor extractor;
    @Autowired RecordedTranscriber recorded;
    @Autowired MetaSpeechTranscriber meta;

    /* ------------------------------------------------------ language detection --- */

    @Test
    void languageIsReadOffTheTranscriptNotPassedIn() {
        // The extractor is never told which language it is parsing, so a model that returns
        // Burmese for an English request is still parsed correctly.
        assertTrue(EntityExtractor.isBurmese("လှည်းတန်းမှ ဆူးလေမြို့တော်ခန်းမကို သွားချင်တယ်"));
        assertFalse(EntityExtractor.isBurmese("I want to go from Hledan to Sule Myodaw Khan Ma"));
        assertFalse(EntityExtractor.isBurmese(""));
        assertFalse(EntityExtractor.isBurmese(null));
    }

    /* ------------------------------------------------------------- extraction --- */

    @Test
    void burmesePostpositionsStillFillBothSlots() {
        var e = extractor.extract("လှည်းတန်းမှ ဆူးလေမြို့တော်ခန်းမကို သွားချင်တယ်");
        assertNotNull(e.originStopId(), "မှ marks the origin");
        assertNotNull(e.destStopId(), "ကို marks the destination");
        assertEquals("လှည်းတန်း", e.originName());
        assertEquals("ဆူးလေမြို့တော်ခန်းမ", e.destName());
    }

    @Test
    void englishPrepositionsFillTheSameSlots() {
        var e = extractor.extract("I want to go from Hledan to Sule Myodaw Khan Ma");
        assertEquals("Hledan", e.originName());
        assertEquals("Sule Myodaw Khan Ma", e.destName());
        assertNotNull(e.originStopId());
        assertNotNull(e.destStopId());
    }

    @Test
    void theTownshipComesOffBecauseNobodySaysIt() {
        // The data calls it "Hledan (Kamayut)" so that four stops named Nat Sin Lan can be
        // told apart. A speaker says "Hledan", so the English pass matches the base name.
        assertEquals("Hledan", EntityExtractor.baseName("Hledan (Kamayut)"));
        assertEquals("Shwe Gon Daing", EntityExtractor.baseName("Shwe Gon Daing (Bahan)"));
        assertEquals("No Brackets", EntityExtractor.baseName("No Brackets"));
    }

    @Test
    void theFirstToInTheSentenceIsNotTheOneThatCounts() {
        // "I want TO go from X TO Y" - stopping at the first "to" would parse "go" as the
        // destination and lose the real one.
        var e = extractor.extract("I want to go from Insein Zay to Botahtaung Paya");
        assertEquals("Insein Zay", e.originName());
        assertEquals("Botahtaung Paya", e.destName());
    }

    @Test
    void aLongerStopNameIsNotShadowedByAShorterOneInsideIt() {
        // "Shwe Gon" must not win inside "Shwe Gon Daing" - the name has to end on a word
        // boundary, or the parse silently picks a different stop on the same road.
        var e = extractor.extract("I want to go from Myaynigone to Shwe Gon Daing");
        assertEquals("Shwe Gon Daing", e.destName());
    }

    @Test
    void aStopNamedWithoutADirectionWordFillsNothing() {
        // Guards against the extractor grabbing any stop name it sees. "Hledan" appears but
        // neither marker precedes it, so there is nothing to conclude.
        var e = extractor.extract("Hledan is very busy today");
        assertNull(e.originStopId());
        assertNull(e.destStopId());
    }

    @Test
    void everyRecordedSampleParsesInBothLanguages() {
        // The regression that actually happened once: two Burmese samples silently stopped
        // resolving when the demo moved to the real YBS export, and the voice step returned
        // understood=false on stage. This fails loudly instead.
        for (Map.Entry<String, String> e : RecordedTranscriber.transcripts("my").entrySet()) {
            var parsed = extractor.extract(e.getValue());
            assertNotNull(parsed.originStopId(), "my/" + e.getKey() + " origin: " + e.getValue());
            assertNotNull(parsed.destStopId(), "my/" + e.getKey() + " destination: " + e.getValue());
        }
        for (Map.Entry<String, String> e : RecordedTranscriber.transcripts("en").entrySet()) {
            var parsed = extractor.extract(e.getValue());
            assertNotNull(parsed.originStopId(), "en/" + e.getKey() + " origin: " + e.getValue());
            assertNotNull(parsed.destStopId(), "en/" + e.getKey() + " destination: " + e.getValue());
        }
    }

    @Test
    void bothLanguagesOfOneSampleReachTheSameTwoStops() {
        // sample1 is the same journey in either language. The stop ids may differ - four
        // stops share a name and the English pass loses the township - but the names must
        // line up, or the two recordings are not the same query.
        var my = extractor.extract(RecordedTranscriber.transcripts("my").get("sample1"));
        var en = extractor.extract(RecordedTranscriber.transcripts("en").get("sample1"));
        assertEquals("Hledan", en.originName());
        assertEquals("လှည်းတန်း", my.originName());
        assertNotNull(my.destStopId());
        assertNotNull(en.destStopId());
    }

    /* ------------------------------------------------------------- MMS client --- */

    @Test
    void mmsLanguageCodesAreIso639_3() {
        // MMS wants mya/eng, the UI speaks my/en, and this mapping lives in exactly one place.
        assertEquals("mya", MetaSpeechTranscriber.adapterFor("my"));
        assertEquals("eng", MetaSpeechTranscriber.adapterFor("en"));
        assertEquals("eng", MetaSpeechTranscriber.adapterFor("EN"));
        assertEquals("eng", MetaSpeechTranscriber.adapterFor("eng"));
    }

    @Test
    void anUnknownLanguageFallsToBurmeseNotEnglish() {
        // The recordings are Burmese. Guessing English for an unknown code would transcribe
        // Burmese audio with an English adapter and return confident nonsense.
        assertEquals("mya", MetaSpeechTranscriber.adapterFor(null));
        assertEquals("mya", MetaSpeechTranscriber.adapterFor("fr"));
        assertEquals("mya", MetaSpeechTranscriber.adapterFor(""));
    }

    @Test
    void mmsFallsBackToTheRecordedTranscriptAndSaysSo() {
        // No MMS server is running in a test run, which is the point: the demo must degrade
        // to the recorded path rather than throwing, and modeName() must admit it happened.
        String burmese = meta.transcribe("sample1", "my");
        assertEquals(RecordedTranscriber.transcripts("my").get("sample1"), burmese);
        assertTrue(meta.modeName().contains("fell back"), meta.modeName());
        assertTrue(meta.modeName().contains("mya"), meta.modeName());

        String english = meta.transcribe("sample1", "en");
        assertEquals(RecordedTranscriber.transcripts("en").get("sample1"), english);
        assertTrue(meta.modeName().contains("eng"), meta.modeName());
    }

    @Test
    void theRecordedPathServesWhicheverLanguageIsAsked() {
        assertTrue(EntityExtractor.isBurmese(recorded.transcribe("sample2", "my")));
        assertFalse(EntityExtractor.isBurmese(recorded.transcribe("sample2", "en")));
        // The no-argument form is the Burmese one, so existing callers keep their behaviour.
        assertEquals(recorded.transcribe("sample2", "my"), recorded.transcribe("sample2"));
    }

    /* ------------------------------------------------------------ the audio --- */

    @Test
    void theSampleAudioIsNotSilence() throws Exception {
        // This is the regression that hid for months. sample1-3.wav shipped as two seconds
        // of digital silence, which was harmless while the voice step was scripted playback
        // of a known transcript - and useless the moment a real recogniser was pointed at
        // them, because an empty transcript from a working model and an empty transcript
        // from a broken one look exactly alike.
        //
        // Checked on the CLASSPATH copy, not on frontend/voice/, because that copy is what
        // MetaSpeechTranscriber actually posts - and regenerating the audio without
        // rebuilding leaves the browser hearing the new clip while the model gets the old one.
        for (String name : new String[]{
                "sample1", "sample2", "sample3",
                "sample1.en", "sample2.en", "sample3.en"}) {
            var res = new org.springframework.core.io.ClassPathResource("static/voice/" + name + ".wav");
            assertTrue(res.exists(), name + ".wav is missing - run tools/voice/synthesize.py");

            try (var in = new java.io.BufferedInputStream(res.getInputStream());
                 var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(in)) {
                byte[] bytes = audio.readAllBytes();
                var fmt = audio.getFormat();
                assertEquals(16_000f, fmt.getSampleRate(), 1f,
                        name + ": wav2vec2 only accepts 16 kHz");

                int peak = 0;
                for (int i = 0; i + 1 < bytes.length; i += 2) {
                    // 16-bit little-endian PCM, which is what synthesize.py writes.
                    peak = Math.max(peak, Math.abs((short) ((bytes[i + 1] << 8) | (bytes[i] & 0xFF))));
                }
                assertTrue(peak > 1000,
                        name + ".wav is silent (peak=" + peak + ") - nothing to transcribe");
            }
        }
    }

    /* --------------------------------------------------------- live speech --- */

    @Test
    void liveSpeechNeverFallsBackToACannedTranscript() {
        // The single most important rule in the voice feature. A packaged sample may answer
        // from memory when the recogniser is down, because its sentence is known in advance.
        // Live speech has no such sentence: replying with someone else's words would invent
        // an understanding that never happened and route the rider somewhere they did not
        // ask to go. No MMS server runs in a test, so this must throw, not return.
        byte[] wav = silentWav(1.0);
        var thrown = assertThrows(IllegalStateException.class,
                () -> meta.transcribeSpoken(wav, "my"));
        assertEquals("recogniser unavailable", thrown.getMessage());
    }

    @Test
    void liveSpeechAndSamplesDisagreeOnPurpose() {
        // Same class, same broken endpoint, deliberately opposite behaviour: the sample path
        // degrades, the live path refuses. If these ever converge, one of them is wrong.
        assertEquals(RecordedTranscriber.transcripts("my").get("sample1"),
                meta.transcribe("sample1", "my"));
        assertThrows(IllegalStateException.class,
                () -> meta.transcribeSpoken(silentWav(0.5), "my"));
    }

    /** A valid 16 kHz mono 16-bit WAV of the requested length, all zeroes. */
    private static byte[] silentWav(double seconds) {
        int rate = 16_000;
        int samples = (int) (rate * seconds);
        var out = java.nio.ByteBuffer.allocate(44 + samples * 2)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(36 + samples * 2);
        out.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(16).putShort((short) 1).putShort((short) 1);
        out.putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        out.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(samples * 2);
        return out.array();
    }

    /* ------------------------------------------- what a real recogniser sounds like --- */

    @Test
    void realRecogniserOutputStillResolvesTheStops() {
        // These are VERBATIM transcripts from facebook/mms-1b-all reading the sample audio -
        // 25-36% character error rate. Exact matching scored 0 of 3 on them, which is the
        // whole reason approximate matching exists: a human reads "လှည့်တမ်" as the stop
        // "လှည်းတန်း", and indexOf never will.
        // Sample 2 survives the round trip completely: both stops out of a 26% CER transcript.
        var two = extractor.extract("အင်းစိမ်ဆေးမှငါ့ပိုတစ်ထောင်ဘုရားကိုမသွားချင်တည်");
        assertEquals("အင်းစိန်ဈေး", two.originName(), "origin from a 26% CER transcript");
        assertEquals("ဗိုလ်တထောင်ဘုရား", two.destName());

        // Sample 1 gets its destination. Its ORIGIN is deliberately given up: "လှည့်တမ်" scores
        // 0.556 against the real stop, below a sentence about dinner scoring 0.600 against a
        // stop it does not contain. Accepting this one means accepting that one.
        var one = extractor.extract("လှည့်တမ်မှသဆူးလေမြို့တော်ခမ်းမောက်ကိုခ်သွားခြင်းတဲ");
        assertEquals("ဆူးလေမြို့တော်ခန်းမ", one.destName(), "destination from a 36% CER transcript");
        assertNull(one.originStopId(), "an origin here would mean the threshold admits false ones");
    }

    @Test
    void aLostPostpositionIsStillTheKnownWeakness() {
        // Sample 3 as MMS actually heard it: the မှ that marks the origin came back as မ, so
        // there is no boundary left to split on and the guess goes wrong. Pinned deliberately
        // - if someone improves marker recovery this test should start failing and be updated,
        // rather than the limitation being quietly forgotten.
        var three = extractor.extract("မြေမညကုန်းမအဖှေကိုယ်တိုင်ကိုသွားခြင်တဲ်");
        assertNotEquals("ရွှေဂုံတိုင်", three.destName(),
                "if this now passes, marker recovery improved - update the docs and this test");
    }

    @Test
    void approximateMatchingDoesNotInventStopsOutOfNothing() {
        // The risk of loosening the match: a sentence with no stop in it must still come back
        // empty rather than confidently routing someone somewhere.
        var e = extractor.extract("ဟိုတယ်ကြီးမှာ ညစာ စားချင်တယ်");
        assertNull(e.originStopId(), "invented an origin: " + e.originName());
        assertNull(e.destStopId(), "invented a destination: " + e.destName());
    }

    @Test
    void perfectTranscriptsStillMatchExactlyNotApproximately() {
        // The recorded path must be unaffected: an exact hit has to win, so a clean transcript
        // never gets a near-miss neighbour instead of the stop it actually names.
        var e = extractor.extract(RecordedTranscriber.transcripts("my").get("sample1"));
        assertEquals("လှည်းတန်း", e.originName());
        assertEquals("ဆူးလေမြို့တော်ခန်းမ", e.destName());
    }

    @Test
    void editDistanceAndSimilarityAgreeOnTheObviousCases() {
        assertEquals(0, EntityExtractor.editDistance("abc", "abc"));
        assertEquals(3, EntityExtractor.editDistance("abc", ""));
        assertEquals(1, EntityExtractor.editDistance("abc", "abd"));
        assertEquals(1.0, EntityExtractor.similarity("abc", "abc"), 1e-9);
        assertEquals(0.0, EntityExtractor.similarity("abc", "xyz"), 1e-9);
        // A name found whole inside a longer string scores 1.0 - which is exactly why a
        // minimum name length is enforced before this is trusted.
        assertEquals(1.0, EntityExtractor.bestWindow("abc", "xxabcxx"), 1e-9);
    }
}
