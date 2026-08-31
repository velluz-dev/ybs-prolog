package com.ybs.demo.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The Meta path: speech to text with <b>MMS</b> (Massively Multilingual Speech), which is
 * the reason this class exists rather than another Whisper endpoint.
 *
 * <p>Whisper's Burmese is poor - Burmese sits in the long tail of its training data - while
 * MMS was built for exactly that tail: {@code facebook/mms-1b-all} carries per-language
 * adapter weights for over 1,100 languages, Burmese among them, and switching language is
 * switching adapter rather than hoping a multilingual decoder guesses right. That makes it
 * the defensible choice for this system, and it is the same model family for both languages
 * the UI offers, so the two are not a Burmese path and an English path with different
 * failure modes.
 *
 * <p><b>Language codes are ISO 639-3</b>, not the two-letter codes the rest of the app uses:
 * MMS wants {@code mya} and {@code eng}. {@link #adapterFor(String)} is the only place that
 * mapping lives.
 *
 * <h2>What this expects to be talking to</h2>
 * A <b>self-hosted</b> inference server - no API key in the repo, and a laptop with the
 * server running locally is still an offline demo. The contract is deliberately the same
 * multipart shape {@link WhisperHttpTranscriber} uses, so one small FastAPI process can
 * serve both routes:
 *
 * <pre>
 * POST {demo.voice.meta.url}
 *   multipart/form-data: file=&lt;wav&gt;, language=mya|eng, model=facebook/mms-1b-all
 * 200 -&gt; {"text": "..."}
 * </pre>
 *
 * <p>{@code docs/VOICE.md} has a working server in about thirty lines of transformers.
 *
 * <p><b>Any failure falls back to the recorded transcript</b>, exactly as the Whisper path
 * does, and {@link #modeName()} says so. A live demo must not hard-fail because an optional
 * GPU box is unreachable, and a silent fallback would be worse than a loud one - it would
 * let someone claim a model ran when it did not.
 */
@Component
public class MetaSpeechTranscriber implements VoiceTranscriber {
    private static final Logger log = LoggerFactory.getLogger(MetaSpeechTranscriber.class);

    /** The Burmese postpositions the server uses to find the two spans in the audio. */
    private static final String FROM_MARKER = "မှ";
    private static final String TO_MARKER = "ကို";

    private final RecordedTranscriber fallback;
    private final NetworkService network;
    private final String endpoint;
    private final String model;
    private final RestClient http;

    /** The stop names the recogniser is allowed to answer with, built once. */
    private volatile String lexicon;

    /** What the constrained pass heard on the last call, or null if it did not run. */
    private volatile Heard lastHeard;

    /**
     * One recognition: the free transcript, plus the two stops the audio was scored
     * against the stop list to find. Either name may be null when the constrained pass
     * could not locate that span.
     */
    public record Heard(String transcript, String originName, String destName) {}

    /** Set when the last call fell back, so the UI can say so honestly. */
    private volatile boolean lastCallFellBack = false;

    /** The language the last call actually asked for, for the same reason. */
    private volatile String lastLanguage = "mya";

    public MetaSpeechTranscriber(
            RecordedTranscriber fallback,
            NetworkService network,
            @Value("${demo.voice.meta.url:http://localhost:9100/v1/audio/transcriptions}") String endpoint,
            @Value("${demo.voice.meta.model:facebook/mms-1b-all}") String model,
            @Value("${demo.voice.meta.timeout-ms:8000}") long timeoutMs) {
        this.fallback = fallback;
        this.network = network;
        this.endpoint = endpoint;
        this.model = model;
        // A demo cannot afford to hang on a box that is powered off but still routable, so
        // the timeout is short and explicit rather than whatever the JDK defaults to.
        this.http = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofMillis(timeoutMs));
                    setReadTimeout(Duration.ofMillis(timeoutMs));
                }})
                .build();
    }

    /**
     * Every distinct Burmese stop name, newline separated.
     *
     * <p>This is the whole point of the constrained pass: the set of things a rider can
     * name here is not open, it is 2,080 stops. Handing that list to the recogniser turns
     * "write down what you heard and hope a lookup matches it" into "of these 824 names,
     * which does this stretch of audio sound most like" - a question that cannot be
     * answered with a place that does not exist.
     *
     * <p>Built once and held: the network does not change while the app is running, and
     * rebuilding a 30 KB string per request would cost more than it saves.
     */
    private String lexicon() {
        String cached = lexicon;
        if (cached == null) {
            cached = network.allStops().stream()
                    .map(Stop::getNameMy)
                    .filter(n -> n != null && n.length() >= 2)
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining("\n"));
            lexicon = cached;
        }
        return cached;
    }

    /** What the constrained pass heard last, or null. Read straight after a transcribe call. */
    public Heard lastHeard() {
        return lastHeard;
    }

    /** Pulls the two names out of the server's {@code constrained} block. */
    @SuppressWarnings("unchecked")
    private Heard readConstrained(Map<?, ?> body, String transcript) {
        Object c = body.get("constrained");
        if (!(c instanceof Map<?, ?> spans)) {
            return new Heard(transcript, null, null);
        }
        return new Heard(transcript, topName(spans.get("origin")), topName(spans.get("destination")));
    }

    private static String topName(Object slot) {
        if (slot instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> best) {
            Object n = best.get("name");
            return n == null ? null : n.toString();
        }
        return null;
    }

    /**
     * The UI speaks {@code my} and {@code en}; MMS speaks ISO 639-3. Anything unrecognised
     * becomes Burmese, because that is what the recorded samples are and guessing English
     * for an unknown code would transcribe Burmese audio with an English adapter and return
     * confident nonsense rather than failing.
     */
    public static String adapterFor(String uiLanguage) {
        return "en".equalsIgnoreCase(uiLanguage) || "eng".equalsIgnoreCase(uiLanguage) ? "eng" : "mya";
    }

    @Override
    public String transcribe(String sampleKey) {
        return transcribe(sampleKey, "my");
    }

    /** @param uiLanguage {@code my} or {@code en} - whichever the page is currently showing. */
    public String transcribe(String sampleKey, String uiLanguage) {
        String language = adapterFor(uiLanguage);
        lastLanguage = language;

        // The English samples are separate recordings, so the file name carries the language.
        String file = "eng".equals(language) ? sampleKey + ".en" : sampleKey;
        Resource audio = new ClassPathResource("static/voice/" + file + ".wav");
        if (!audio.exists()) {
            audio = new ClassPathResource("static/voice/" + sampleKey + ".wav");
        }

        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", audio);
            form.add("model", model);
            form.add("language", language);
            addLexicon(form, language);

            // contentType + the MultiValueMap directly. Wrapping it in an HttpEntity is the
            // RestTemplate idiom; RestClient treats that wrapper as an ordinary object and
            // asks Jackson to serialise it, which fails on the stream inside the file part.
            Map<?, ?> body = http.post()
                    .uri(endpoint)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            Object text = body == null ? null : body.get("text");
            if (text == null || text.toString().isBlank()) {
                throw new IllegalStateException("empty transcript from " + endpoint);
            }
            lastCallFellBack = false;
            String transcript = text.toString().trim();
            lastHeard = readConstrained(body, transcript);
            log.info("Transcribed [{}] (MMS {}): {}  constrained={}",
                    sampleKey, language, transcript, body.get("constrained"));
            return transcript;

        } catch (Exception e) {
            lastCallFellBack = true;
            lastHeard = null;
            log.warn("MMS endpoint {} unavailable ({}), falling back to the recorded transcript",
                    endpoint, e.getMessage());
            return fallback.transcribe(sampleKey, uiLanguage);
        }
    }

    /**
     * Transcribe audio the rider just spoke, rather than one of the packaged samples.
     *
     * <p><b>This one does not fall back, and that is the point.</b> Everywhere else in this
     * class an unreachable endpoint quietly becomes the recorded transcript, which is right
     * for a sample whose words are known in advance. For live speech it would be a lie: the
     * app would answer "I want to go from Hledan to Sule" to someone who said something else
     * entirely, and route them confidently to the wrong place. There is no honest default for
     * audio nobody has heard, so a failure here stays a failure and the UI says the recogniser
     * is unavailable.
     *
     * @param wav        16 kHz mono PCM, as the browser encodes it before upload
     * @param uiLanguage {@code my} or {@code en}
     * @throws IllegalStateException if the recogniser is unreachable or returns nothing
     */
    public String transcribeSpoken(byte[] wav, String uiLanguage) {
        String language = adapterFor(uiLanguage);
        lastLanguage = language;

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new NamedByteArrayResource(wav, "speech.wav"));
        form.add("model", model);
        form.add("language", language);
        addLexicon(form, language);

        Map<?, ?> body;
        try {
            body = http.post()
                    .uri(endpoint)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            lastCallFellBack = true;
            log.warn("MMS endpoint {} unavailable for live speech ({})", endpoint, e.getMessage());
            throw new IllegalStateException("recogniser unavailable", e);
        }

        // The server reports silence explicitly rather than as an empty string, so a muted
        // microphone can be told apart from a model that produced nothing.
        if (body != null && Boolean.TRUE.equals(body.get("silent"))) {
            lastCallFellBack = false;
            throw new IllegalStateException("silent");
        }

        Object text = body == null ? null : body.get("text");
        if (text == null || text.toString().isBlank()) {
            lastCallFellBack = false;
            throw new IllegalStateException("empty transcript");
        }
        lastCallFellBack = false;
        String transcript = text.toString().trim();
        lastHeard = readConstrained(body, transcript);
        log.info("Transcribed live speech ({} bytes, {}): {}  constrained={}",
                wav.length, language, transcript, body.get("constrained"));
        return transcript;
    }

    /**
     * The constrained pass is Burmese-only for now. English stop names are the same words
     * a rider says, so the same trick applies - but the English adapter already reads the
     * Latin names close enough for the ordinary match, and an untested second lexicon is a
     * change without a measurement behind it.
     */
    private void addLexicon(MultiValueMap<String, Object> form, String language) {
        if (!"mya".equals(language)) {
            return;
        }
        form.add("lexicon", lexicon());
        form.add("from_marker", FROM_MARKER);
        form.add("to_marker", TO_MARKER);
    }

    /**
     * A byte[] that still has a filename. Spring's multipart encoder needs one to write a
     * file part; without it the bytes go out as a plain field and the server sees no file.
     */
    private static final class NamedByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    @Override
    public String modeName() {
        String base = "mms:" + lastLanguage;
        return lastCallFellBack ? base + " (fell back to recorded)" : base;
    }
}
