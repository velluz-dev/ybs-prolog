package com.ybs.demo.ops;

/**
 * Incident categories the demo seeds and counts.
 *
 * <p>FLOOD matters beyond reporting: a flooded incident is asserted into the Prolog
 * engine as {@code flooded(Segment)}, which the {@code violates/2} rule family then
 * uses to rule journeys out. The others are dashboard-only.
 */
public enum IncidentType {
    BREAKDOWN,
    CROWDING,
    FLOOD,
    BUNCHING,
    ACCIDENT,
    ROADWORK,
    TRAFFIC
}
