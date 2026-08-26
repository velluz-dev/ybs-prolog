package com.ybs.demo;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Route;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.network.Stop;
import com.ybs.demo.planning.AStarPlanner;
import com.ybs.demo.planning.StopGraph;
import com.ybs.demo.shared.GeoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The heuristic must never overestimate, or A* stops being optimal. This checks that
 * claim against every reachable stop pair in the seeded network rather than trusting
 * the argument in the javadoc.
 */
@SpringBootTest
class AStarHeuristicTest {

    @Autowired NetworkService network;
    @Autowired StopGraph graph;
    @Autowired AStarPlanner planner;

    @Test
    void heuristicNeverOverestimatesTrueCost() {
        List<Stop> stops = network.allStops();
        assertFalse(stops.isEmpty(), "network should be seeded");

        int checked = 0;
        for (Stop origin : stops) {
            for (Stop destination : stops) {
                if (origin.getId().equals(destination.getId())) continue;

                AStarPlanner.Result result = planner.plan(origin.getId(), destination.getId());
                if (result.journeys().isEmpty()) continue;   // unreachable pair, nothing to compare

                double actual = result.journeys().stream()
                        .mapToDouble(j -> j.totalMinutes()).min().orElseThrow();

                double km = GeoUtils.haversineKm(origin.getLat(), origin.getLon(),
                        destination.getLat(), destination.getLon());
                double h = km / graph.heuristicSpeedKmh() * 60.0;

                assertTrue(h <= actual + 1e-6,
                        () -> String.format("h overestimated for %s -> %s: h=%.3f but true cost=%.3f",
                                origin.getNameEn(), destination.getNameEn(), h, actual));
                checked++;
            }
        }
        assertTrue(checked > 100, "expected to check many pairs, only checked " + checked);
    }

    /**
     * The pair test above only exercises whatever traffic bucket happens to be current
     * when it runs, which is exactly how the original night-time bug survived a green
     * build. This checks every segment against every bucket, so it fails at any hour.
     */
    @Test
    void heuristicSpeedBeatsEverySegmentInEveryTrafficBucket() {
        for (String bucket : List.of("morning_peak", "midday", "evening_peak", "night")) {
            for (var route : network.allRoutes()) {
                var itinerary = network.itinerary(route.getId());
                for (int idx = 0; idx + 1 < itinerary.size(); idx++) {
                    final int i = idx;
                    double minutes = graph.rideMinutes(route.getCode(), i, bucket);
                    if (minutes <= 0) continue;

                    var a = itinerary.get(i).getStop();
                    var b = itinerary.get(i + 1).getStop();
                    double km = GeoUtils.haversineKm(a.getLat(), a.getLon(), b.getLat(), b.getLon());
                    double actualSpeed = km / (minutes / 60.0);

                    assertTrue(actualSpeed <= graph.heuristicSpeedKmh() + 1e-6,
                            () -> String.format(
                                    "route %s segment %d in %s runs at %.2f km/h, above the heuristic bound %.2f",
                                    route.getCode(), i, bucket, actualSpeed, graph.heuristicSpeedKmh()));
                }
            }
        }
    }

    @Test
    void maxSegmentSpeedIsAnUpperBoundOnEverySegment() {
        // The admissibility argument rests on this: no segment beats maxSegmentSpeedKmh.
        for (var route : network.allRoutes()) {
            var itinerary = network.itinerary(route.getId());
            for (int i = 0; i + 1 < itinerary.size(); i++) {
                double minutes = graph.freeFlowRideMinutes(route.getCode(), i);
                if (minutes <= 0) continue;
                double km = GeoUtils.haversineKm(
                        itinerary.get(i).getStop().getLat(), itinerary.get(i).getStop().getLon(),
                        itinerary.get(i + 1).getStop().getLat(), itinerary.get(i + 1).getStop().getLon());
                double speed = km / (minutes / 60.0);
                assertTrue(speed <= graph.maxSegmentSpeedKmh() + 1e-6,
                        "segment faster than the claimed maximum on route " + route.getCode());
            }
        }
    }

    /**
     * Regression for the ridden-flag fix: boarding a bus must count as a transfer only
     * when another bus was already boarded. One bus the whole way is zero transfers -
     * including when the rider walked to a nearby stop first, which no direct-board
     * alternative can dominate away.
     */
    @Test
    void singleRouteJourneyHasZeroTransfers() {
        // Route 36 runs Insein Market (1) -> Sule (12); one bus, end to end.
        Long origin = 1L, dest = 12L;
        AStarPlanner.Result result = planner.plan(origin, dest);
        assertFalse(result.journeys().isEmpty(), "route 36 endpoints must be reachable");

        boolean zeroTransferSingleRoute = result.journeys().stream().anyMatch(j ->
                j.transferCount() == 0 && j.routeCodes().size() == 1);
        assertTrue(zeroTransferSingleRoute,
                "a one-bus journey must appear with 0 transfers on the Pareto front");

        // And nothing on the front may claim negative or nonsense transfer counts.
        result.journeys().forEach(j ->
                assertTrue(j.transferCount() >= 0, "transfer count must never be negative"));
    }
}
