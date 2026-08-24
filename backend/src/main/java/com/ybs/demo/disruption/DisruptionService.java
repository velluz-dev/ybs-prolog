package com.ybs.demo.disruption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Disruptions that are currently in force.
 *
 * <p>Kept separate from the seeded {@code incident} table on purpose. Those rows are a
 * historical log used for the ops dashboard's counts; treating every past flood as still
 * active would rule out every journey the moment the app starts, which is both wrong and
 * a poor demo.
 *
 * <p>A flood asserted here becomes {@code flooded(sNN)} in the rule base, which the
 * {@code violates/2} family already knows how to act on. That is the point of the button:
 * nothing in the search or the UI is told what a flood means - only the rule base is, and
 * the journeys still disappear.
 */
@Service
public class DisruptionService {
    private static final Logger log = LoggerFactory.getLogger(DisruptionService.class);

    private final Set<Long> floodedStops = Collections.synchronizedSet(new LinkedHashSet<>());

    public Set<Long> floodedStops() {
        synchronized (floodedStops) {
            return new LinkedHashSet<>(floodedStops);
        }
    }

    public void setFlooded(Long stopId, boolean active) {
        if (active) {
            floodedStops.add(stopId);
        } else {
            floodedStops.remove(stopId);
        }
        log.info("Flood at stop {} {}", stopId, active ? "ASSERTED" : "cleared");
    }

    public void clearAll() {
        floodedStops.clear();
        log.info("All disruptions cleared");
    }
}
