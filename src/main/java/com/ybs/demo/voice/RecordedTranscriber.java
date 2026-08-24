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
    private static final Map<String, String> TRANSCRIPTS = new LinkedHashMap<>(Map.of(
            "sample1", "ကျောက်တံတားမှ လှည်းတန်းကို သွားချင်တယ်",
            "sample2", "အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်",
            "sample3", "လှည်းတန်းမှ ဆူးလေဘုရားကို သွားချင်တယ်"));

    public static Map<String, String> transcripts() {
        return TRANSCRIPTS;
    }

    @Override
    public String transcribe(String sampleKey) {
        String transcript = TRANSCRIPTS.getOrDefault(sampleKey, TRANSCRIPTS.get("sample1"));
        try {
            Thread.sleep(FAKE_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Transcribed [{}] (recorded): {}", sampleKey, transcript);
        return transcript;
    }

    @Override
    public String modeName() {
        return "recorded";
    }
}
