package com.ybs.demo.planning;

import java.util.List;

/**
 * One complete option offered to the rider.
 *
 * <p>The three fields the Pareto filter compares are {@code totalMinutes},
 * {@code totalFareKyats} and {@code transferCount}. They genuinely trade off against each
 * other - the fastest journey is often not the cheapest - which is why the planner returns
 * a set rather than collapsing them into one weighted "best" answer that hides the choice.
 */
public record Journey(
        String id,
        List<Leg> legs,
        double totalMinutes,
        double etaLowerBound,
        double etaUpperBound,
        int totalFareKyats,
        int transferCount,
        List<String> routeCodes,
        List<Long> stopIds) {

    /** One continuous piece of the journey: a bus ride, or a walk between stops. */
    public record Leg(
            String kind,           // "ride" | "walk"
            String routeCode,      // null for a walk
            Long fromStopId,
            Long toStopId,
            String fromNameMy,
            String toNameMy,
            String fromNameEn,
            String toNameEn,
            double minutes,
            int fareKyats,
            /** True when this ride runs the route end to end - what the fare rules price higher. */
            boolean fullRoute,
            List<Long> viaStopIds) {}
}
