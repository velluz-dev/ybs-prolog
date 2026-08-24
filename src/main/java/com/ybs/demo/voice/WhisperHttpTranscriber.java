package com.ybs.demo.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * The online path: posts the audio file to a self-hosted Whisper endpoint
 * (whisper.cpp or faster-whisper, both of which expose the OpenAI-compatible
 * {@code /v1/audio/transcriptions} route).
 *
 * <p>Self-hosted rather than a cloud provider, so there is no API key in the repo and the
 * whole thing can still run on a laptop with no internet if the server is local.
 *
 * <p><b>Any failure falls back to the recorded transcript</b> rather than propagating. A
 * live demo must not hard-fail because an optional service is down; the response says
 * which path actually produced the text, so the fallback is never silent.
 */
@Component
public class WhisperHttpTranscriber implements VoiceTranscriber {
    private static final Logger log = LoggerFactory.getLogger(WhisperHttpTranscriber.class);

    private final RecordedTranscriber fallback;
    private final String endpoint;
    private final String model;
    private final RestClient http = RestClient.create();

    /** Set when the last call fell back, so the UI can say so honestly. */
    private volatile boolean lastCallFellBack = false;

    public WhisperHttpTranscriber(RecordedTranscriber fallback,
                                  @Value("${demo.voice.stt.url:http://localhost:9000/v1/audio/transcriptions}") String endpoint,
                                  @Value("${demo.voice.stt.model:whisper-1}") String model) {
        this.fallback = fallback;
        this.endpoint = endpoint;
        this.model = model;
    }

    @Override
    public String transcribe(String sampleKey) {
        Resource audio = new ClassPathResource("static/voice/" + sampleKey + ".wav");
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", audio);
            form.add("model", model);
            form.add("language", "my");     // Burmese

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            Map<?, ?> body = http.post()
                    .uri(endpoint)
                    .body(new HttpEntity<>(form, headers))
                    .retrieve()
                    .body(Map.class);

            Object text = body == null ? null : body.get("text");
            if (text == null || text.toString().isBlank()) {
                throw new IllegalStateException("empty transcript from " + endpoint);
            }
            lastCallFellBack = false;
            log.info("Transcribed [{}] (whisper): {}", sampleKey, text);
            return text.toString().trim();

        } catch (Exception e) {
            lastCallFellBack = true;
            log.warn("Whisper endpoint {} unavailable ({}), falling back to the recorded transcript",
                    endpoint, e.getMessage());
            return fallback.transcribe(sampleKey);
        }
    }

    @Override
    public String modeName() {
        return lastCallFellBack ? "whisper (fell back to recorded)" : "whisper";
    }
}
