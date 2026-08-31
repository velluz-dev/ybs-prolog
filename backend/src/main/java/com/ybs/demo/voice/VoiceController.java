package com.ybs.demo.voice;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final MetaSpeechTranscriber meta;
    private final EntityExtractor extractor;
    private final NetworkService network;
    private final String configuredMode;

    public VoiceController(RecordedTranscriber recorded,
                           WhisperHttpTranscriber whisper,
                           MetaSpeechTranscriber meta,
                           EntityExtractor extractor,
                           NetworkService network,
                           @Value("${demo.voice.mode:recorded}") String configuredMode) {
        this.recorded = recorded;
        this.whisper = whisper;
        this.meta = meta;
        this.extractor = extractor;
        this.network = network;
        this.configuredMode = configuredMode;
    }

    /**
     * The three demo queries, in whichever language the page is showing.
     *
     * @param lang {@code my} or {@code en}
     */
    @GetMapping("/samples")
    public Map<String, Object> samples(@RequestParam(defaultValue = "my") String lang) {
        return Map.of("mode", configuredMode, "lang", lang,
                "samples", RecordedTranscriber.transcripts(lang));
    }

    /**
     * @param mode {@code recorded}, {@code whisper} or {@code mms} - overrides
     *             {@code demo.voice.mode} for this one request, so the offline path can be
     *             forced on stage without a restart
     * @param lang {@code my} or {@code en}; picks the recording, the MMS language adapter,
     *             and which half of the extractor parses the result
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestParam(defaultValue = "sample1") String sample,
                                     @RequestParam(required = false) String mode,
                                     @RequestParam(defaultValue = "my") String lang) {
        String effective = mode == null ? configuredMode : mode;
        String transcript;
        VoiceTranscriber transcriber;

        if ("mms".equalsIgnoreCase(effective) || "meta".equalsIgnoreCase(effective)) {
            transcriber = meta;
            transcript = meta.transcribe(sample, lang);
        } else if ("whisper".equalsIgnoreCase(effective)) {
            transcriber = whisper;
            transcript = whisper.transcribe(sample);
        } else {
            transcriber = recorded;
            transcript = recorded.transcribe(sample, lang);
        }

        EntityExtractor.Entities entities =
                preferHeard(extractor.extract(transcript), meta.lastHeard());

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

    /**
     * Live voice: the rider speaks, the browser uploads what it captured, and the recogniser
     * reads it back. The packaged samples remain for a demo with no microphone.
     *
     * <p>Unlike {@link #query}, this <b>never</b> falls back to a canned transcript. A sample
     * has a known sentence behind it, so answering from memory when the model is down costs
     * nothing. Live speech has no such sentence: replying with someone else's words would
     * invent an understanding the system never had, and send the rider somewhere they did not
     * ask for. So a missing recogniser is reported as one.
     *
     * @param audio 16 kHz mono PCM WAV, encoded by the browser before upload
     * @param lang  {@code my} or {@code en} - picks the MMS language adapter
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> transcribeSpoken(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(defaultValue = "my") String lang) throws IOException {

        if (audio.isEmpty()) {
            return problem("empty_audio");
        }

        String transcript;
        try {
            transcript = meta.transcribeSpoken(audio.getBytes(), lang);
        } catch (IllegalStateException e) {
            // "silent" is the rider's microphone; anything else is the recogniser.
            return problem("silent".equals(e.getMessage()) ? "silent" : "recogniser_unavailable");
        }

        EntityExtractor.Entities entities =
                preferHeard(extractor.extract(transcript), meta.lastHeard());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", meta.modeName());
        out.put("live", true);
        out.put("transcript", transcript);
        out.put("originStopId", entities.originStopId());
        out.put("originName", entities.originName());
        out.put("destStopId", entities.destStopId());
        out.put("destName", entities.destName());
        out.put("understood", entities.originStopId() != null && entities.destStopId() != null);
        return ResponseEntity.ok(out);
    }

    /**
     * 503 with a machine-readable reason. The UI translates the reason; it must never be
     * shown an empty transcript and left to guess whether the model or the microphone failed.
     */
    private ResponseEntity<Map<String, Object>> problem(String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("understood", false);
        out.put("reason", reason);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(out);
    }

    /**
     * Prefer what the audio said over what the transcript spelled.
     *
     * <p>When the recogniser has scored the stop list against the frames, its answer is a
     * real stop chosen by sound. {@link EntityExtractor} can only compare spellings, and at
     * a 30% character error rate that is a losing game - measured over the twelve samples,
     * the acoustic answer gets both stops right 10 times against 5 for string matching.
     *
     * <p>The extractor still runs and still owns English, the recorded path, and any span
     * the constrained pass could not locate. This only overrides a slot when there is an
     * acoustic answer for it that names a stop we actually have.
     */
    private EntityExtractor.Entities preferHeard(EntityExtractor.Entities parsed,
                                                 MetaSpeechTranscriber.Heard heard) {
        if (heard == null) {
            return parsed;
        }
        Stop origin = stopNamed(heard.originName());
        Stop dest = stopNamed(heard.destName());
        if (origin == null && dest == null) {
            return parsed;
        }
        return new EntityExtractor.Entities(
                origin != null ? origin.getId() : parsed.originStopId(),
                origin != null ? origin.getNameMy() : parsed.originName(),
                dest != null ? dest.getId() : parsed.destStopId(),
                dest != null ? dest.getNameMy() : parsed.destName());
    }

    /** First stop with this exact Burmese name. The recogniser can only return real ones. */
    private Stop stopNamed(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return network.allStops().stream()
                .filter(s -> name.equals(s.getNameMy()))
                .findFirst().orElse(null);
    }
}
