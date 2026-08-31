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
import java.util.Random;

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

    /**
     * Checks h never overestimates, on a deterministic random sample of stop pairs.
     *
     * <p>It used to check every pair, which was right for a 28-stop network. On the real
     * 2,080-stop network that is four million searches - so it samples instead. The seed is
     * fixed, so a failure is reproducible rather than a coin toss that a teammate cannot
     * repeat. The exhaustive guarantee now comes from
     * {@link #heuristicSpeedBeatsEverySegmentInEveryTrafficBucket()}, which checks every
     * segment directly and is what actually pins admissibility down.
     */
    @Test
    void heuristicNeverOverestimatesTrueCost() {
        List<Stop> stops = network.allStops();
        assertFalse(stops.isEmpty(), "network should be seeded");

        Random random = new Random(20260826L);
        int checked = 0;
        for (int i = 0; i < 40; i++) {
            Stop origin = stops.get(random.nextInt(stops.size()));
            Stop destination = stops.get(random.nextInt(stops.size()));
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
        assertTrue(checked > 5, "expected several reachable pairs, only checked " + checked);
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

    /**
     * The end-to-end fare depends on this being derived, not guessed.
     *
     * <p>Checked against every route in the seeded network: only the first-stop-to-last-stop
     * pair counts as riding the whole route, and shaving either end off it must not.
     *
     * <p>Worth knowing while reading this: every YBS route is a loop, so a route's first and
     * last stop are the same place on the ground. That makes the full-route rate correct but
     * unreachable through {@code /api/journeys/search} - A* walks the few hundred metres
     * between the termini instead of riding the whole loop round. See the fare notes in
     * {@code ybs.pl}.
     */
    @Test
    void onlyTheWholeItineraryCountsAsAFullRouteRide() {
        for (Route r : network.allRoutes()) {
            List<RouteStop> it = network.itinerary(r.getId());
            if (it.size() < 3) continue;
            Long first = it.get(0).getStop().getId();
            Long last = it.get(it.size() - 1).getStop().getId();
            Long second = it.get(1).getStop().getId();
            Long penultimate = it.get(it.size() - 2).getStop().getId();

            assertTrue(network.spansWholeRoute(r.getId(), first, last),
                    "route " + r.getCode() + ": first -> last is the whole route");
            assertFalse(network.spansWholeRoute(r.getId(), second, last),
                    "route " + r.getCode() + ": boarding one stop late is not the whole route");
            assertFalse(network.spansWholeRoute(r.getId(), first, penultimate),
                    "route " + r.getCode() + ": alighting one stop early is not the whole route");
            // Only meaningful when the loop does not close on the very same stop id: for a
            // route that literally begins and ends at one stop, first -> last reversed IS
            // first -> last, and there is no direction left to get wrong.
            if (!first.equals(last)) {
                assertFalse(network.spansWholeRoute(r.getId(), last, first),
                        "route " + r.getCode() + ": the itinerary has a direction");
            }
        }
    }
}
