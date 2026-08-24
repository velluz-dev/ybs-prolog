package com.ybs.demo.planning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class JourneyController {

    private final JourneyPlanningService planning;

    public JourneyController(JourneyPlanningService planning) {
        this.planning = planning;
    }

    /**
     * Plans a journey, filters it through the rule base, and returns the search trace.
     *
     * <p>The trace and the A*-vs-Dijkstra expansion counts come back with the results on
     * purpose: the frontend renders them, so the examiner watches the frontier grow and
     * sees what the heuristic bought.
     *
     * @param profile repeatable rider flags, e.g. {@code &profile=pregnant&profile=student}
     */
    @GetMapping("/journeys/search")
    public Map<String, Object> search(@RequestParam Long from,
                                      @RequestParam Long to,
                                      @RequestParam(required = false) List<String> profile) {
        Set<String> flags = profile == null ? Set.of() : new HashSet<>(profile);
        return planning.plan(from, to, flags);
    }
}
