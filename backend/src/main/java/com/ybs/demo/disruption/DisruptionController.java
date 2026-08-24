package com.ybs.demo.disruption;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Injects a disruption into the world so the rule base can react to it live.
 *
 * <p>This exists for one demo moment: flood a stop, re-run the search, and watch the
 * journeys that use it disappear with the reason {@code flooded_segment}. Nothing in the
 * planner or the UI knows what a flood means - only {@code ybs.pl} does.
 */
@RestController
@RequestMapping("/api")
public class DisruptionController {

    private final DisruptionService disruptions;

    public DisruptionController(DisruptionService disruptions) {
        this.disruptions = disruptions;
    }

    @GetMapping("/disruptions")
    public Map<String, Object> list() {
        return Map.of("floodedStops", disruptions.floodedStops());
    }

    @PostMapping("/disruptions/flood")
    public Map<String, Object> flood(@RequestParam Long stopId,
                                     @RequestParam(defaultValue = "true") boolean active) {
        disruptions.setFlooded(stopId, active);
        return Map.of("floodedStops", disruptions.floodedStops());
    }

    @PostMapping("/disruptions/clear")
    public Map<String, Object> clear() {
        disruptions.clearAll();
        return Map.of("floodedStops", disruptions.floodedStops());
    }
}
