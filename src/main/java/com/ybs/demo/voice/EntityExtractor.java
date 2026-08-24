package com.ybs.demo.voice;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pulls {origin, destination} out of a Burmese transcript.
 *
 * <p>This is string matching against the known stop names, not an NLP model, and the demo
 * says so. Two Burmese postpositions carry the direction:
 * <ul>
 *   <li>{@code မှ} - "from", marks the origin
 *   <li>{@code ကို} - "to", marks the destination
 * </ul>
 *
 * <p>Burmese is written without spaces between words, so tokenising is not an option. The
 * approach instead is: find every stop name that appears anywhere in the string, note the
 * character that follows it, and let the postposition decide which slot it fills. With 28
 * known stops this is reliable, and it is honest about being a lookup.
 *
 * <p>Longest names are matched first so that "ဆူးလေဘုရား" (Sule Pagoda) is not shadowed
 * by a shorter name that happens to be a substring of it.
 */
@Component
public class EntityExtractor {
    private static final Logger log = LoggerFactory.getLogger(EntityExtractor.class);

    private static final String FROM_MARKER = "မှ";
    private static final String TO_MARKER = "ကို";

    private final NetworkService network;

    public EntityExtractor(NetworkService network) {
        this.network = network;
    }

    public record Entities(Long originStopId, String originName,
                           Long destStopId, String destName) {}

    public Entities extract(String transcript) {
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
        }

        log.info("Parsed entities from \"{}\" -> origin={} ({}), destination={} ({})",
                transcript, originName, originId, destName, destId);
        return new Entities(originId, originName, destId, destName);
    }
}
