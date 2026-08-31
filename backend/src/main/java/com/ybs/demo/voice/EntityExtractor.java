package com.ybs.demo.voice;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pulls {origin, destination} out of a transcript, in Burmese or English.
 *
 * <p>This is string matching against the known stop names, not an NLP model, and the demo
 * says so. What differs between the two languages is only where the direction marker sits.
 *
 * <p><b>Burmese</b> puts it after the name, as a postposition:
 * <ul>
 *   <li>{@code မှ} - "from", marks the origin
 *   <li>{@code ကို} - "to", marks the destination
 * </ul>
 * Burmese is written without spaces between words, so tokenising is not an option. The
 * approach instead is: find every stop name that appears anywhere in the string, note the
 * characters that follow it, and let the postposition decide which slot it fills.
 *
 * <p><b>English</b> puts it in front, as a preposition - "from Hledan to Sule" - so the
 * scan looks at what precedes the name instead. English also needs the township dropped:
 * the data calls a stop "Hledan (Kamayut)" because four stops share the name Nat Sin Lan
 * and the township is what separates them, but nobody says the bracket out loud. So the
 * English pass matches the base name and the disambiguation is lost - which is honest, and
 * is why a spoken English query resolves to the first stop of that name rather than
 * pretending to know which of the two Hledans was meant.
 *
 * <p>Which pass runs is decided by the transcript itself rather than by a parameter: a
 * string containing Burmese code points is Burmese. That keeps the extractor callable from
 * anywhere without threading a language through, and it stays right if a model returns
 * Burmese for an English request or the other way round.
 *
 * <p>Longest names are matched first, in both passes, so that "ဆူးလေမြို့တော်ခန်းမ" is not
 * shadowed by a shorter name that happens to be a substring of it.
 */
@Component
public class EntityExtractor {
    private static final Logger log = LoggerFactory.getLogger(EntityExtractor.class);

    private static final String FROM_MARKER = "မှ";
    private static final String TO_MARKER = "ကို";

    /** U+1000..U+109F is the Myanmar block. One character from it settles the language. */
    private static final int MYANMAR_BLOCK_START = 0x1000;
    private static final int MYANMAR_BLOCK_END = 0x109F;

    private final NetworkService network;

    public EntityExtractor(NetworkService network) {
        this.network = network;
    }

    public record Entities(Long originStopId, String originName,
                           Long destStopId, String destName) {}

    public Entities extract(String transcript) {
        boolean burmese = isBurmese(transcript);
        Entities entities = burmese ? extractBurmese(transcript) : extractEnglish(transcript);

        // Exact matching is right for a transcript whose words are known - the packaged
        // samples - and hopeless against a real recogniser. MMS hears "လှည့်တမ်" for the stop
        // "လှည်းတန်း": a human reads those as the same place, indexOf does not. So when a slot
        // is still empty, try again allowing for the recogniser being approximately right.
        if (entities.originStopId() == null || entities.destStopId() == null) {
            Entities fuzzy = burmese ? fuzzyBurmese(transcript, entities)
                                     : fuzzyEnglish(transcript, entities);
            if (fuzzy != null) {
                entities = fuzzy;
            }
        }

        log.info("Parsed entities from \"{}\" -> origin={} ({}), destination={} ({})",
                transcript, entities.originName(), entities.originStopId(),
                entities.destName(), entities.destStopId());
        return entities;
    }

    /** True if any character is in the Myanmar Unicode block. */
    public static boolean isBurmese(String text) {
        if (text == null) {
            return false;
        }
        return text.chars().anyMatch(c -> c >= MYANMAR_BLOCK_START && c <= MYANMAR_BLOCK_END);
    }

    /** Direction marker follows the name: "…လှည်းတန်း<b>မှ</b> …ဆူးလေ…<b>ကို</b>". */
    private Entities extractBurmese(String transcript) {
        Long originId = null, destId = null;
        String originName = null, destName = null;

        // Longest first, so a name that contains another does not lose to it.
        var stops = network.allStops().stream()
                .sorted((a, b) -> b.getNameMy().length() - a.getNameMy().length())
                .toList();

        for (Stop stop : stops) {
            String name = stop.getNameMy();
            int at = transcript.indexOf(name);
            if (at < 0) {
                continue;
            }
            String after = transcript.substring(at + name.length());

            if (after.startsWith(FROM_MARKER) && originId == null) {
                originId = stop.getId();
                originName = name;
            } else if (after.startsWith(TO_MARKER) && destId == null) {
                destId = stop.getId();
                destName = name;
            }
            if (originId != null && destId != null) {
                break;   // both slots filled; the rest of the scan cannot improve the parse
            }
        }
        return new Entities(originId, originName, destId, destName);
    }

    /**
     * Direction marker precedes the name: "from <b>Hledan</b> to <b>Sule Myodaw Khan Ma</b>".
     *
     * <p>Matched case-insensitively against the name with its township dropped, and only
     * where the marker sits immediately before it - so "Botahtaung Paya" in
     * "…to Botahtaung Paya" fills the destination and a stop named in passing fills nothing.
     */
    private Entities extractEnglish(String transcript) {
        String haystack = transcript.toLowerCase();
        Long originId = null, destId = null;
        String originName = null, destName = null;

        var stops = network.allStops().stream()
                .filter(s -> s.getNameEn() != null && !s.getNameEn().isBlank())
                .sorted((a, b) -> baseName(b.getNameEn()).length() - baseName(a.getNameEn()).length())
                .toList();

        for (Stop stop : stops) {
            String name = baseName(stop.getNameEn());
            if (name.isBlank()) {
                continue;
            }
            String needle = name.toLowerCase();

            if (originId == null && precededBy(haystack, needle, "from ")) {
                originId = stop.getId();
                originName = name;
            } else if (destId == null && precededBy(haystack, needle, "to ")) {
                destId = stop.getId();
                destName = name;
            }
            if (originId != null && destId != null) {
                break;
            }
        }
        return new Entities(originId, originName, destId, destName);
    }

    /** "Hledan (Kamayut)" -&gt; "Hledan". Nobody says the township out loud. */
    public static String baseName(String nameEn) {
        int bracket = nameEn.indexOf('(');
        return (bracket < 0 ? nameEn : nameEn.substring(0, bracket)).trim();
    }

    /**
     * Does {@code needle} appear immediately after {@code marker} anywhere in {@code text}?
     *
     * <p>Every occurrence is checked, not just the first: "to go from Hledan to Sule" has a
     * "to" before "go" and another before "Sule", and stopping at the first match of the
     * name would miss the one that counts.
     */
    private static boolean precededBy(String text, String needle, String marker) {
        String phrase = marker + needle;
        int at = text.indexOf(phrase);
        while (at >= 0) {
            int end = at + phrase.length();
            // The name must end at a word boundary, or "to Shwe Gon" would match
            // "to Shwe Gon Daing" and take the shorter stop.
            if (end >= text.length() || !Character.isLetterOrDigit(text.charAt(end))) {
                return true;
            }
            at = text.indexOf(phrase, at + 1);
        }
        return false;
    }

    /* ------------------------------------------------------ approximate matching --- */

    /**
     * How close a recognised span has to be to a stop name before we believe it.
     *
     * <p>Measured against real MMS output, not chosen by taste, and the measurement settles an
     * argument that cannot be won by tuning. Reading "ဟိုတယ်ကြီးမှာ ညစာ စားချင်တယ်" - a sentence
     * about dinner, containing no stop at all - the nearest stop name scores <b>0.600</b>. The
     * genuine match of "လှည့်တမ်" (what the recogniser heard) to the stop "လှည်းတန်း" scores
     * <b>0.556</b>. The false positive scores <i>higher than the true positive</i>, so no
     * threshold keeps one and rejects the other.
     *
     * <p>Given that, this sits above the false positive rather than below the true one. The
     * cost is recall: the weakest real matches are refused and the rider is asked to repeat
     * themselves. The alternative is a system that confidently routes someone to a place they
     * never named, and on a bus network that is the worse failure by a distance - a missed
     * word costs a retry, a wrong stop costs an afternoon.
     *
     * <p>Raising recall needs a better transcript, not a lower number here.
     */
    private static final double MIN_SIMILARITY = 0.66;

    /**
     * Names shorter than this are not matched approximately. A three-character name turns up
     * by accident inside almost any sentence - "ဘုရ" scored a perfect 1.0 against a transcript
     * that did not contain that stop at all - and a false stop is worse than no stop.
     */
    private static final int MIN_FUZZY_NAME = 4;

    /** Burmese: origin is what precedes မှ, destination is what sits between မှ and ကို. */
    private Entities fuzzyBurmese(String transcript, Entities exact) {
        String t = transcript;
        int from = t.indexOf(FROM_MARKER);
        int to = t.indexOf(TO_MARKER, from < 0 ? 0 : from + FROM_MARKER.length());

        // No postposition, no guess. The recogniser can lose it altogether - it heard "မ" for
        // "မှ" in one of the samples - and without it there is nothing to say WHICH stop is
        // the origin, only that some stop name is in there somewhere. Splitting the sentence
        // down the middle and matching each half was tried; it turned "I want dinner at the
        // big hotel" into a journey. A sentence we cannot parse must stay unparsed.
        if (from < 0) {
            return null;
        }
        String originSpan = t.substring(0, from);
        String destSpan = t.substring(from + FROM_MARKER.length(), to > 0 ? to : t.length());

        Long originId = exact.originStopId();
        String originName = exact.originName();
        Long destId = exact.destStopId();
        String destName = exact.destName();

        if (originId == null) {
            Stop hit = bestInSpan(originSpan, true);
            if (hit != null) { originId = hit.getId(); originName = hit.getNameMy(); }
        }
        if (destId == null) {
            Stop hit = bestInSpan(destSpan, true);
            if (hit != null) { destId = hit.getId(); destName = hit.getNameMy(); }
        }
        return new Entities(originId, originName, destId, destName);
    }

    /** English: origin follows "from", destination follows the "to" that comes after it. */
    private Entities fuzzyEnglish(String transcript, Entities exact) {
        String t = transcript.toLowerCase();
        int from = t.indexOf("from ");
        int to = t.indexOf(" to ", from < 0 ? 0 : from);

        // Same rule as the Burmese side: without "from" there is no origin to speak of.
        // "Hledan is very busy today" names a stop and asks for nothing.
        if (from < 0) {
            return null;
        }
        String originSpan = t.substring(from + 5, to > 0 ? to : t.length());
        String destSpan = to > 0 ? t.substring(to + 4) : "";

        Long originId = exact.originStopId();
        String originName = exact.originName();
        Long destId = exact.destStopId();
        String destName = exact.destName();

        if (originId == null) {
            Stop hit = bestInSpan(originSpan, false);
            if (hit != null) { originId = hit.getId(); originName = baseName(hit.getNameEn()); }
        }
        if (destId == null) {
            Stop hit = bestInSpan(destSpan, false);
            if (hit != null) { destId = hit.getId(); destName = baseName(hit.getNameEn()); }
        }
        return new Entities(originId, originName, destId, destName);
    }

    /** The stop whose name best matches any part of this span, or null if nothing is close. */
    private Stop bestInSpan(String span, boolean burmese) {
        if (span == null || span.isBlank()) {
            return null;
        }
        String haystack = burmese ? span : span.toLowerCase();
        Stop best = null;
        double bestScore = MIN_SIMILARITY;

        for (Stop stop : network.allStops()) {
            String raw = burmese ? stop.getNameMy() : stop.getNameEn();
            if (raw == null || raw.isBlank()) continue;
            String name = burmese ? raw : baseName(raw).toLowerCase();
            if (name.length() < MIN_FUZZY_NAME) continue;

            double score = bestWindow(name, haystack);
            // Ties go to the longer name: a short name matching perfectly inside a longer
            // one is the accident this guards against, not a better answer.
            if (score > bestScore
                    || (best != null && score == bestScore
                        && name.length() > (burmese ? best.getNameMy() : baseName(best.getNameEn())).length())) {
                bestScore = score;
                best = stop;
            }
        }
        return best;
    }

    /**
     * Best similarity between {@code name} and any substring of {@code text} of roughly the
     * same length. Widths within three characters are tried, because a recogniser drops and
     * doubles characters as readily as it substitutes them.
     */
    public static double bestWindow(String name, String text) {
        int n = name.length();
        double best = 0;
        for (int w = Math.max(2, n - 3); w <= n + 3; w++) {
            for (int i = 0; i + 1 <= text.length(); i++) {
                int end = Math.min(text.length(), i + w);
                if (end - i < 2) break;
                double sim = similarity(name, text.substring(i, end));
                if (sim > best) best = sim;
                if (end == text.length()) break;
            }
        }
        return best;
    }

    /** 1 - (edit distance / longer length). 1.0 is identical, 0.0 shares nothing. */
    public static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0 : 1.0 - (double) editDistance(a, b) / max;
    }

    /** Levenshtein, one row at a time - the strings here are stop names, never long. */
    public static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev; prev = cur; cur = swap;
        }
        return prev[b.length()];
    }
}
