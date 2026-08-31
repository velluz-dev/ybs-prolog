package com.ybs.demo.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The offline path, and the default.
 *
 * <p>Three pre-recorded queries with known transcripts. No model runs here and the demo
 * does not pretend one does - this exists so the voice step cannot fail on stage because
 * of a network or an API key. The 800 ms delay is deliberate: without it the transcript
 * appears instantly and the step reads as fake.
 */
@Component
public class RecordedTranscriber implements VoiceTranscriber {
    private static final Logger log = LoggerFactory.getLogger(RecordedTranscriber.class);

    private static final long FAKE_LATENCY_MS = 800;

    /** Sample key -> the transcript of the matching file in static/voice/. */
    /**
     * Each sample must name stops that actually exist, because EntityExtractor matches
     * against the seeded stop names. Two of these silently stopped resolving when the demo
     * moved to the real YBS export - "ကျောက်တံတား" and "ဆူးလေဘုရား" are not stop names in
     * that dataset (Sule is "ဆူးလေမြို့တော်ခန်းမ") - and the voice demo returned
     * understood=false. If you reseed the database, re-check these.
     */
    private static final Map<String, String> TRANSCRIPTS = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("sample1", "လှည်းတန်းမှ ဆူးလေမြို့တော်ခန်းမကို သွားချင်တယ်"),
            Map.entry("sample2", "အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်"),
            Map.entry("sample3", "မြေနီကုန်းမှ ရွှေဂုံတိုင်ကို သွားချင်တယ်"),
            Map.entry("sample4", "ဘုရင့်နောင်မှ ကျောက်ကုန်းကို သွားချင်တယ်"),
            Map.entry("sample5", "မန္တလေးလမ်းမှ စံရိပ်ငြိမ်ကို သွားချင်တယ်"),
            Map.entry("sample6", "အလုံစာတိုက်မှ ပိတောက်ကွေ့ကို သွားချင်တယ်"),
            Map.entry("sample7", "နွားခြံမှ မာန်ပြေကို သွားချင်တယ်"),
            Map.entry("sample8", "ထူပါရုံမှ သဲကွင်းကို သွားချင်တယ်"),
            Map.entry("sample9", "နီလာမှ မာလာကို သွားချင်တယ်"),
            Map.entry("sample10", "ကုန်ဈေးတန်းမှ လက်မှတ်ဂိတ်ကို သွားချင်တယ်"),
            Map.entry("sample11", "ဦးထွန်းမြတ်မှ စံပြဈေးကွေ့ကို သွားချင်တယ်"),
            Map.entry("sample12", "ရဲစခန်းမှ မီးဒုံးကို သွားချင်တယ်")));

    /**
     * The same queries in English, for when the page is in English.
     *
     * <p>They are not translations for display - they are what the English recordings say,
     * and {@link EntityExtractor} has to be able to parse them back into the same two stops.
     * The names here are the stops' {@code name_en} with the township dropped, because that
     * is how someone actually says them out loud.
     *
     * <p><b>Twelve, not three.</b> Three clips were too few to tune anything on: the
     * approximate-matching threshold was being chosen from two data points, which is taste
     * dressed as measurement. These span short, middle and long stop names on purpose -
     * a four-character name is the case the matcher finds hardest, and a set of only long
     * ones would have flattered it.
     */
    private static final Map<String, String> TRANSCRIPTS_EN = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("sample1", "I want to go from Hledan to Sule Myodaw Khan Ma"),
            Map.entry("sample2", "I want to go from Insein Zay to Botahtaung Paya"),
            Map.entry("sample3", "I want to go from Myaynigone to Shwe Gon Daing"),
            Map.entry("sample4", "I want to go from Bayint Naung to Kyauk Kone"),
            Map.entry("sample5", "I want to go from Mandalay Lan to San Yeik Nyein"),
            Map.entry("sample6", "I want to go from Ahlone Sar Taik to Padauk Kwae"),
            Map.entry("sample7", "I want to go from Nwar Chan to Man Pyae"),
            Map.entry("sample8", "I want to go from Htu Par Yon to Thal Kwin"),
            Map.entry("sample9", "I want to go from Nilar to Marlar"),
            Map.entry("sample10", "I want to go from Kone Zay Tan to Lat Hmat Gate"),
            Map.entry("sample11", "I want to go from U Tun Myat to Sanpya Zay Kwae"),
            Map.entry("sample12", "I want to go from Ye Sa Khan to Mee Don")));

    public static Map<String, String> transcripts() {
        return TRANSCRIPTS;
    }

    /** @param uiLanguage {@code my} or {@code en}. */
    public static Map<String, String> transcripts(String uiLanguage) {
        return "en".equalsIgnoreCase(uiLanguage) ? TRANSCRIPTS_EN : TRANSCRIPTS;
    }

    @Override
    public String transcribe(String sampleKey) {
        return transcribe(sampleKey, "my");
    }

    /** @param uiLanguage {@code my} or {@code en} - which recording is being played back. */
    public String transcribe(String sampleKey, String uiLanguage) {
        Map<String, String> table = transcripts(uiLanguage);
        String transcript = table.getOrDefault(sampleKey, table.get("sample1"));
        try {
            Thread.sleep(FAKE_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Transcribed [{}] (recorded, {}): {}", sampleKey, uiLanguage, transcript);
        return transcript;
    }

    @Override
    public String modeName() {
        return "recorded";
    }
}
