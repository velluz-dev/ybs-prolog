package com.ybs.demo.voice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Voice query: audio -> transcript -> entities -> (the frontend then runs the search).
 *
 * <p>Which transcriber is used is chosen per request from {@code demo.voice.mode}, so the
 * offline path can be forced without a restart. Default is {@code recorded}.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final RecordedTranscriber recorded;
    private final WhisperHttpTranscriber whisper;
    private final EntityExtractor extractor;
    private final String configuredMode;

    public VoiceController(RecordedTranscriber recorded,
                           WhisperHttpTranscriber whisper,
                           EntityExtractor extractor,
                           @Value("${demo.voice.mode:recorded}") String configuredMode) {
        this.recorded = recorded;
        this.whisper = whisper;
        this.extractor = extractor;
        this.configuredMode = configuredMode;
    }

    /** The three demo queries, so the UI can offer them. */
    @GetMapping("/samples")
    public Map<String, Object> samples() {
        return Map.of("mode", configuredMode, "samples", RecordedTranscriber.transcripts());
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestParam(defaultValue = "sample1") String sample,
                                     @RequestParam(required = false) String mode) {
        String effective = mode == null ? configuredMode : mode;
        VoiceTranscriber transcriber = "whisper".equalsIgnoreCase(effective) ? whisper : recorded;

        String transcript = transcriber.transcribe(sample);
        EntityExtractor.Entities entities = extractor.extract(transcript);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", transcriber.modeName());
        out.put("transcript", transcript);
        out.put("originStopId", entities.originStopId());
        out.put("originName", entities.originName());
        out.put("destStopId", entities.destStopId());
        out.put("destName", entities.destName());
        out.put("understood", entities.originStopId() != null && entities.destStopId() != null);
        return out;
    }
}
