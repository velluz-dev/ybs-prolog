package com.ybs.demo.planning;

import com.ybs.demo.reasoning.FactAssembler;
import com.ybs.demo.reasoning.PrologService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ties the two AI components together: A* proposes, Prolog disposes.
 *
 * <p>The search is not told about the rider at all. It produces the Pareto set purely on
 * time, fare and transfers, and the rule base then rules options in or out. Keeping them
 * separate is what makes the demo's central move possible - flipping a profile toggle
 * re-filters the *same* search results, so the change visibly comes from the reasoning
 * layer and not from re-running the search with different parameters.
 */
@Service
public class JourneyPlanningService {
    private static final Logger log = LoggerFactory.getLogger(JourneyPlanningService.class);

    private static final String RIDER = "rider1";

    private final AStarPlanner planner;
    private final PrologService prolog;
    private final FactAssembler facts;

    public JourneyPlanningService(AStarPlanner planner, PrologService prolog, FactAssembler facts) {
        this.planner = planner;
        this.prolog = prolog;
        this.facts = facts;
    }

    public Map<String, Object> plan(Long fromStopId, Long toStopId, Set<String> riderFlags) {
        AStarPlanner.Result result = planner.plan(fromStopId, toStopId);

        // One clean world per request. Without this, facts from the previous query would
        // still be in the engine and the next answer would look uncannily well-informed.
        facts.resetWorld();
        facts.assertRider(RIDER, riderFlags);
        for (Journey j : result.journeys()) {
            facts.assertJourney(j);
        }

        List<Map<String, Object>> assessed = new ArrayList<>();
        for (Journey j : result.journeys()) {
            boolean suitable = prolog.prove("suitable(" + j.id() + ", " + RIDER + ")");
            List<String> reasons = new ArrayList<>();
            if (!suitable) {
                for (Map<String, String> s :
                        prolog.solveAll("violation_reason(" + j.id() + ", " + RIDER + ", Why)", 10)) {
                    String why = s.get("Why");
                    if (why != null && !reasons.contains(why)) {
                        reasons.add(why);
                    }
                }
            }

            // The fare each RIDE leg costs this particular rider, per the fare rules -
            // a monk's total is 0 however many buses the journey uses.
            int riderFare = 0;
            for (Journey.Leg leg : j.legs()) {
                if ("ride".equals(leg.kind()) && leg.routeCode() != null) {
                    for (Map<String, String> s :
                            prolog.solveAll("fare(" + RIDER + ", '" + leg.routeCode() + "', F)", 1)) {
                        riderFare += parseIntOr(s.get("F"), leg.fareKyats());
                    }
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("journey", j);
            m.put("suitable", suitable);
            m.put("violationReasons", reasons);
            m.put("riderFareKyats", riderFare);
            assessed.add(m);
        }

        log.info("Planned {} -> {} for flags {}: {} option(s), {} suitable",
                fromStopId, toStopId, riderFlags, assessed.size(),
                assessed.stream().filter(a -> Boolean.TRUE.equals(a.get("suitable"))).count());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("options", assessed);
        out.put("trace", result.trace());
        out.put("expansions", result.expansions());
        out.put("dijkstraExpansions", result.dijkstraExpansions());
        out.put("bucket", result.bucket());
        return out;
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
