package com.ybs.demo.planning;

import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Route;
import com.ybs.demo.network.Stop;
import com.ybs.demo.shared.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* over the stop graph.
 *
 * <h2>Heuristic and why it is admissible</h2>
 * <pre>h(n) = haversine(stop(n), destination) / heuristicSpeed</pre>
 *
 * <p>A* only guarantees an optimal path if h never overestimates the true remaining cost.
 * This one cannot, for two independent reasons that compose:
 *
 * <ol>
 *   <li><b>The distance is a lower bound.</b> Haversine is the straight line over the
 *       Earth's surface. No sequence of bus segments and walks can cover less ground than
 *       the straight line, so the real remaining distance is always &gt;= this.
 *   <li><b>The speed is an upper bound.</b> {@code heuristicSpeed} is not a guess. It is
 *       the fastest free-flow segment speed in the seeded network, <b>divided by the
 *       smallest traffic factor in the prior</b>.
 * </ol>
 *
 * <p>That division matters and it is easy to get wrong - this code did, at first. Off-peak
 * traffic factors are below 1.0 (0.90 to 0.95 at night). They multiply segment times
 * <i>down</i>, so a bus at night travels <i>faster</i> than its free-flow base time
 * implies. Using the free-flow maximum alone therefore was not an upper bound at all, and
 * {@code AStarHeuristicTest} caught h overestimating by 0.7 minutes on the North Okkalapa
 * to Thingangyun segment during the night bucket. Dividing by the minimum factor restores
 * a genuine ceiling on achievable speed in any bucket.
 *
 * <p>Dividing a lower-bound distance by an upper-bound speed yields a lower bound on
 * remaining time. Every other cost the planner charges - transfer penalties, fares, walking
 * - is non-negative and ignored by h, which can only make h smaller still. So
 * {@code h(n) <= trueCost(n, goal)} everywhere, and A* returns an optimal path.
 *
 * <p>(The heuristic is also consistent, since it comes from a metric divided by a constant,
 * so no node ever needs re-expanding.)
 */
@Service
public class AStarPlanner {
    private static final Logger log = LoggerFactory.getLogger(AStarPlanner.class);

    private static final int MAX_EXPANSIONS = 5000;

    private final StopGraph graph;
    private final NetworkService network;
    private final EtaService eta;

    public AStarPlanner(StopGraph graph, NetworkService network, EtaService eta) {
        this.graph = graph;
        this.network = network;
        this.eta = eta;
    }

    /**
     * A node in the search: standing at a stop, either on a route or on foot.
     */
    private record Node(Long stopId, String routeCode) {}

    /**
     * Everything known about the best path found so far to a node.
     *
     * @param ridden whether a bus has been boarded anywhere on the path so far - what
     *               distinguishes the journey's first boarding (free of penalty) from a
     *               genuine transfer, including when the rider walks to a nearby stop first
     * @param hv     the heuristic at this node, computed once at creation. The open-set
     *               comparator runs on every poll and every insertion; recomputing a
     *               haversine per comparison is wasted work for an identical answer.
     */
    private record Label(Node node, double minutes, int fare, int transfers, boolean ridden,
                         Label parent, String how, double hv) {}

    /** One line of the visible search trace. */
    public record TraceEntry(int order, Long stopId, String stopName, String routeCode,
                             double g, double h, double f) {}

    public record Result(List<Journey> journeys, List<TraceEntry> trace, int expansions,
                         int dijkstraExpansions, String bucket) {}

    /**
     * Runs A* several times with different transfer penalties, then keeps only the
     * non-dominated results. One run cannot produce a Pareto set: a single scalar cost
     * function has a single optimum. Varying what a transfer costs is the cheapest way to
     * sweep out the fare/time/transfer trade-off, because transfers are what actually
     * couple those three quantities in this network.
     */
    public Result plan(Long originStopId, Long destStopId) {
        String bucket = EtaService.currentBucket();
        List<Journey> candidates = new ArrayList<>();
        List<TraceEntry> trace = null;
        int expansions = 0;

        double[] penalties = {
                StopGraph.TRANSFER_PENALTY_MINUTES,   // the honest default
                40.0,                                  // strongly prefer a one-seat ride
                2.0                                    // tolerate changes to save time
        };

        for (double penalty : penalties) {
            Search search = search(originStopId, destStopId, bucket, penalty, false);
            if (trace == null) {
                trace = search.trace;          // show the trace of the default run
                expansions = search.expansions;
            }
            if (search.goal != null) {
                candidates.add(toJourney(search.goal, bucket, candidates.size()));
            }
        }

        // Same search with h = 0 is exactly Dijkstra. Running it purely to report how many
        // more nodes it expands is what turns "we used a heuristic" into a number.
        Search dijkstra = search(originStopId, destStopId, bucket, StopGraph.TRANSFER_PENALTY_MINUTES, true);

        List<Journey> pareto = paretoFilter(dedupe(candidates));
        log.info("Plan {} -> {}: {} candidate(s), {} on the Pareto front; A* expanded {} nodes vs Dijkstra {}",
                originStopId, destStopId, candidates.size(), pareto.size(), expansions, dijkstra.expansions);

        return new Result(pareto, trace == null ? List.of() : trace, expansions, dijkstra.expansions, bucket);
    }

    private static class Search {
        Label goal;
        List<TraceEntry> trace = new ArrayList<>();
        int expansions;
    }

    private Search search(Long originStopId, Long destStopId, String bucket,
                          double transferPenalty, boolean uninformed) {
        Search out = new Search();
        Stop destination = network.stop(destStopId);
        if (destination == null || network.stop(originStopId) == null) {
            return out;
        }

        Map<Node, Label> best = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        PriorityQueue<Label> open = new PriorityQueue<>(
                Comparator.comparingDouble(l -> l.minutes() + l.hv()));

        Label start = new Label(new Node(originStopId, null), 0, 0, 0, false, null, "start",
                h(originStopId, destination, uninformed));
        open.add(start);
        best.put(start.node(), start);

        while (!open.isEmpty() && out.expansions < MAX_EXPANSIONS) {
            Label current = open.poll();
            if (closed.contains(current.node())) {
                continue;
            }
            closed.add(current.node());
            out.expansions++;

            double g = current.minutes();
            double hv = h(current.node().stopId(), destination, uninformed);
            Stop s = network.stop(current.node().stopId());
            out.trace.add(new TraceEntry(out.expansions, current.node().stopId(),
                    s == null ? "?" : s.getNameEn(), current.node().routeCode(),
                    round(g), round(hv), round(g + hv)));

            // Goal: standing at the destination, off the bus.
            if (current.node().stopId().equals(destStopId) && current.node().routeCode() == null) {
                out.goal = current;
                return out;
            }

            for (Label next : successors(current, bucket, transferPenalty, destination, uninformed)) {
                Label prior = best.get(next.node());
                boolean better = prior == null
                        || next.minutes() < prior.minutes() - 1e-9
                        || (Math.abs(next.minutes() - prior.minutes()) <= 1e-9
                            && (next.fare() < prior.fare()
                                || (next.fare() == prior.fare()
                                    && next.transfers() < prior.transfers())));
                if (better) {
                    // On an exact tie in minutes the cheaper, less transfer-heavy path wins,
                    // so the Pareto filter sees the best candidate of each shape.
                    best.put(next.node(), next);
                    open.add(next);
                }
            }
        }
        return out;
    }

    private List<Label> successors(Label current, String bucket, double transferPenalty,
                                   Stop destination, boolean uninformed) {
        List<Label> out = new ArrayList<>();
        Long stopId = current.node().stopId();
        String routeCode = current.node().routeCode();

        if (routeCode == null) {
            // Board any route calling here. The journey's first boarding is not a transfer -
            // and neither is boarding after walking to a nearby stop, since no bus has been
            // boarded yet; that is what the ridden flag tracks.
            for (String rc : graph.routesAt(stopId)) {
                Route route = network.routeByCode(rc);
                if (route == null) continue;
                boolean isTransfer = current.ridden();
                double penalty = isTransfer ? transferPenalty : 0.0;
                out.add(new Label(new Node(stopId, rc),
                        current.minutes() + penalty,
                        current.fare() + route.getBaseFareKyats(),
                        current.transfers() + (isTransfer ? 1 : 0),
                        true, current, "board " + rc, current.hv()));
            }
            // Walk to a nearby stop.
            for (StopGraph.WalkLink w : graph.walksFrom(stopId)) {
                out.add(new Label(new Node(w.toStopId(), null),
                        current.minutes() + w.minutes(),
                        current.fare(), current.transfers(), current.ridden(), current, "walk",
                        h(w.toStopId(), destination, uninformed)));
            }
        } else {
            // Ride one segment onward.
            List<Long> ids = graph.stopsOf(routeCode);
            int index = ids.indexOf(stopId);
            if (index >= 0 && index + 1 < ids.size()) {
                double minutes = graph.rideMinutes(routeCode, index, bucket);
                out.add(new Label(new Node(ids.get(index + 1), routeCode),
                        current.minutes() + minutes,
                        current.fare(), current.transfers(), true, current, "ride " + routeCode,
                        h(ids.get(index + 1), destination, uninformed)));
            }
            // Get off here.
            out.add(new Label(new Node(stopId, null),
                    current.minutes(), current.fare(), current.transfers(), current.ridden(),
                    current, "alight", current.hv()));
        }
        return out;
    }

    /** See the class javadoc for the admissibility argument. */
    private double h(Long stopId, Stop destination, boolean uninformed) {
        if (uninformed) {
            return 0.0;  // h = 0 turns A* into Dijkstra
        }
        Stop s = network.stop(stopId);
        if (s == null) {
            return 0.0;
        }
        double km = GeoUtils.haversineKm(s.getLat(), s.getLon(), destination.getLat(), destination.getLon());
        return km / graph.heuristicSpeedKmh() * 60.0;
    }

    private Journey toJourney(Label goal, String bucket, int index) {
        List<Label> path = new ArrayList<>();
        for (Label l = goal; l != null; l = l.parent()) {
            path.add(0, l);
        }

        List<Journey.Leg> legs = new ArrayList<>();
        List<Long> stopIds = new ArrayList<>();
        List<String> routeCodes = new ArrayList<>();

        // Collapse consecutive "ride R" steps into one leg per route.
        int i = 0;
        while (i < path.size()) {
            Label l = path.get(i);
            if (l.how().startsWith("ride ") || l.how().equals("walk")) {
                String kind = l.how().equals("walk") ? "walk" : "ride";
                String rc = kind.equals("ride") ? l.how().substring(5) : null;
                Long from = path.get(i - 1).node().stopId();
                double minutes = 0;
                List<Long> via = new ArrayList<>();
                int j = i;
                while (j < path.size() && path.get(j).how().equals(l.how())) {
                    minutes += path.get(j).minutes() - path.get(j - 1).minutes();
                    via.add(path.get(j).node().stopId());
                    j++;
                }
                Long to = via.get(via.size() - 1);
                int fare = 0;
                if (kind.equals("ride")) {
                    Route r = network.routeByCode(rc);
                    fare = r == null ? 0 : r.getBaseFareKyats();
                    routeCodes.add(rc);
                }
                Stop fs = network.stop(from);
                Stop ts = network.stop(to);
                legs.add(new Journey.Leg(kind, rc, from, to,
                        fs.getNameMy(), ts.getNameMy(), fs.getNameEn(), ts.getNameEn(),
                        round(minutes), fare, via));
                if (stopIds.isEmpty()) stopIds.add(from);
                stopIds.addAll(via);
                i = j;
            } else {
                i++;
            }
        }

        EtaService.Estimate estimate = eta.estimate(goal.minutes(), bucket);
        return new Journey("j" + (index + 1), legs,
                round(goal.minutes()), estimate.etaLowerBound(), estimate.etaUpperBound(),
                goal.fare(), goal.transfers(), routeCodes, stopIds);
    }

    /** Two runs can land on the same itinerary; keep one of each. */
    private List<Journey> dedupe(List<Journey> journeys) {
        Map<String, Journey> byShape = new LinkedHashMap<>();
        for (Journey j : journeys) {
            byShape.putIfAbsent(j.routeCodes() + "|" + j.stopIds(), j);
        }
        return new ArrayList<>(byShape.values());
    }

    /**
     * Keeps only journeys that nothing else beats on all three objectives at once.
     * A journey is dropped if another is at least as good on time, fare and transfers,
     * and strictly better on one of them.
     */
    private List<Journey> paretoFilter(List<Journey> journeys) {
        List<Journey> front = new ArrayList<>();
        for (Journey a : journeys) {
            boolean dominated = journeys.stream().anyMatch(b -> b != a && dominates(b, a));
            if (!dominated) {
                front.add(a);
            }
        }
        front.sort(Comparator.comparingDouble(Journey::totalMinutes));
        return front;
    }

    private static boolean dominates(Journey b, Journey a) {
        boolean noWorse = b.totalMinutes() <= a.totalMinutes()
                && b.totalFareKyats() <= a.totalFareKyats()
                && b.transferCount() <= a.transferCount();
        boolean strictlyBetter = b.totalMinutes() < a.totalMinutes()
                || b.totalFareKyats() < a.totalFareKyats()
                || b.transferCount() < a.transferCount();
        return noWorse && strictlyBetter;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
