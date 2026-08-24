package com.ybs.demo.planning;

import com.ybs.demo.eta.DwellModel;
import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Route;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.network.Stop;
import com.ybs.demo.shared.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The search graph A* runs over.
 *
 * <p>A node is a <b>(stop, route)</b> pair, not just a stop. {@code routeCode == null}
 * means "standing at this stop, not on a bus". That distinction is what makes transfers
 * explicit and therefore chargeable: you cannot change route without passing through the
 * null-route node, which is exactly where the transfer penalty and the next fare are applied.
 *
 * <p>Three edge kinds:
 * <ul>
 *   <li><b>ride</b>   (stop_i, R) -> (stop_i+1, R) - one segment of route R
 *   <li><b>board</b>  (stop, null) -> (stop, R)    - costs a fare, and a transfer penalty
 *                     unless it is the first boarding
 *   <li><b>alight</b> (stop, R) -> (stop, null)    - free
 *   <li><b>walk</b>   (stopA, null) -> (stopB, null) - stops within WALK_RADIUS_M
 * </ul>
 */
@Service
public class StopGraph {
    private static final Logger log = LoggerFactory.getLogger(StopGraph.class);

    /**
     * Minutes added for each transfer after the first boarding.
     *
     * <p>8 minutes is not a measured wait. It encodes the whole cost of changing bus:
     * the expected wait at the new stop, the walk across the junction, and - the part
     * riders care about most - the risk of not getting on a full bus. Pricing a transfer
     * at only its wait time makes the planner cheerfully suggest three-leg journeys that
     * no real rider would accept.
     */
    public static final double TRANSFER_PENALTY_MINUTES = 8.0;

    /**
     * Two stops closer than this are joined by a walking edge.
     *
     * <p>The brief suggested ~200m. At this network's density that yields no walking
     * edges at all, leaving the code untestable. 400m is the standard planning figure
     * for an acceptable walk to transit (roughly 5 minutes) and is the same radius used
     * by the arrival geofence, so the demo has one consistent notion of "walkable". It
     * produces exactly one real edge here: Sule Pagoda to Kyauktada, 344m apart downtown.
     */
    public static final double WALK_RADIUS_M = 400.0;

    /** Unhurried walking pace, km/h. */
    public static final double WALK_SPEED_KMH = 4.5;

    private final NetworkService network;
    private final EtaService eta;
    private final DwellModel dwellModel;

    /** Route code -> ordered stop ids. */
    private final Map<String, List<Long>> routeStopIds = new HashMap<>();
    /** Stop id -> route codes calling there. */
    private final Map<Long, List<String>> routesAtStop = new HashMap<>();
    /** Stop id -> reachable-on-foot (stop id, minutes). */
    private final Map<Long, List<WalkLink>> walkLinks = new HashMap<>();
    /** "routeCode:index" -> free-flow minutes for that segment. */
    private final Map<String, Double> segmentMinutes = new HashMap<>();

    /**
     * The fastest speed any segment achieves under free-flow timings, in km/h.
     * Computed from the data rather than guessed.
     */
    private double maxSegmentSpeedKmh = 1.0;

    /**
     * The speed the A* heuristic divides by: {@link #maxSegmentSpeedKmh} adjusted for the
     * most favourable traffic factor in the prior.
     *
     * <p>Free-flow speed alone is <b>not</b> an upper bound on what the planner can
     * achieve. Off-peak factors are below 1.0 (0.90-0.95 at night), which multiply segment
     * times down and therefore multiply effective speed <i>up</i>. Using free-flow speed
     * directly made the heuristic overestimate on the North Okkalapa - Thingangyun segment
     * at night, which {@code AStarHeuristicTest} caught. Dividing by the minimum factor
     * gives the genuine ceiling.
     */
    private double heuristicSpeedKmh = 1.0;

    public StopGraph(NetworkService network, EtaService eta, DwellModel dwellModel) {
        this.network = network;
        this.eta = eta;
        this.dwellModel = dwellModel;
    }

    @Order(3)  // after NetworkService has cached the itineraries
    @EventListener(ApplicationReadyEvent.class)
    public void build() {
        for (Route route : network.allRoutes()) {
            List<RouteStop> itinerary = network.itinerary(route.getId());
            List<Long> ids = new ArrayList<>();
            for (RouteStop rs : itinerary) {
                ids.add(rs.getStop().getId());
                routesAtStop.computeIfAbsent(rs.getStop().getId(), k -> new ArrayList<>()).add(route.getCode());
            }
            routeStopIds.put(route.getCode(), ids);

            for (int i = 0; i + 1 < itinerary.size(); i++) {
                double minutes = network.baseSegmentMinutes(route.getId(), i);
                segmentMinutes.put(route.getCode() + ":" + i, minutes);

                double km = GeoUtils.haversineKm(
                        itinerary.get(i).getStop().getLat(), itinerary.get(i).getStop().getLon(),
                        itinerary.get(i + 1).getStop().getLat(), itinerary.get(i + 1).getStop().getLon());
                if (minutes > 0) {
                    maxSegmentSpeedKmh = Math.max(maxSegmentSpeedKmh, km / (minutes / 60.0));
                }
            }
        }

        List<Stop> stops = network.allStops();
        int walkEdges = 0;
        for (Stop a : stops) {
            for (Stop b : stops) {
                if (a.getId().equals(b.getId())) continue;
                double metres = GeoUtils.haversineKm(a.getLat(), a.getLon(), b.getLat(), b.getLon()) * 1000.0;
                if (metres <= WALK_RADIUS_M) {
                    double minutes = (metres / 1000.0) / WALK_SPEED_KMH * 60.0;
                    walkLinks.computeIfAbsent(a.getId(), k -> new ArrayList<>()).add(new WalkLink(b.getId(), minutes));
                    walkEdges++;
                }
            }
        }

        double minFactor = Math.min(1.0, eta.minTrafficFactor());
        heuristicSpeedKmh = maxSegmentSpeedKmh / minFactor;

        log.info("Stop graph built: {} routes, {} stops, {} walking edges; fastest segment {} km/h, "
                        + "min traffic factor {} -> heuristic speed bound {} km/h",
                routeStopIds.size(), stops.size(), walkEdges,
                String.format("%.1f", maxSegmentSpeedKmh), String.format("%.2f", minFactor),
                String.format("%.1f", heuristicSpeedKmh));
    }

    /** Fastest free-flow segment speed. Used by the admissibility test, not by the heuristic. */
    public double maxSegmentSpeedKmh() {
        return maxSegmentSpeedKmh;
    }

    /** The bound the A* heuristic divides by. See {@link #heuristicSpeedKmh}. */
    public double heuristicSpeedKmh() {
        return heuristicSpeedKmh;
    }

    public List<String> routesAt(Long stopId) {
        return routesAtStop.getOrDefault(stopId, List.of());
    }

    public List<WalkLink> walksFrom(Long stopId) {
        return walkLinks.getOrDefault(stopId, List.of());
    }

    public List<Long> stopsOf(String routeCode) {
        return routeStopIds.getOrDefault(routeCode, List.of());
    }

    /**
     * Cost of riding one segment: driving time scaled by the traffic prior, <b>plus the
     * dwell at the stop the bus pulls into</b>.
     *
     * <p>Planning and the moving buses use one model, so a corridor the prior calls slow is
     * slow in both, and a journey through eleven stops is quoted with eleven stops' worth of
     * standing at the kerb. Leaving dwell out here would make the planner quietly optimistic:
     * the simulated bus would always arrive later than the plan promised.
     *
     * <p>Dwell depends on how full the bus is, which cannot be known before the rider boards,
     * so the planner assumes standing room - see {@code DwellModel.ASSUMED_CROWDING_FOR_PLANNING}.
     *
     * <p>Charging dwell at the <i>arriving</i> stop slightly over-counts the final stop of a
     * journey, where the rider gets off rather than waiting for the bus to load. That is
     * about 20-45 seconds on a journey of well over an hour, and the alternative - special
     * -casing the goal stop inside an edge cost - would make the edge depend on the query,
     * which is a far worse trade for a search that has to stay explainable.
     */
    public double rideMinutes(String routeCode, int index, String bucket) {
        Double base = segmentMinutes.get(routeCode + ":" + index);
        if (base == null || base <= 0) {
            return 0;
        }
        List<Long> ids = stopsOf(routeCode);
        String segId = EtaService.segmentId(routeCode, ids.get(index), ids.get(index + 1));
        return eta.segmentMinutes(base, segId, bucket) + dwellModel.plannedDwellMinutes(ids.get(index + 1));
    }

    /** Free-flow minutes, ignoring the traffic prior - used by the admissibility test. */
    public double freeFlowRideMinutes(String routeCode, int index) {
        return segmentMinutes.getOrDefault(routeCode + ":" + index, 0.0);
    }

    public record WalkLink(Long toStopId, double minutes) {}
}
