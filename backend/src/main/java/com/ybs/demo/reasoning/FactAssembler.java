package com.ybs.demo.reasoning;

import com.ybs.demo.disruption.DisruptionService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Route;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.ops.Driver;
import com.ybs.demo.ops.DriverMonthlyScore;
import com.ybs.demo.ops.DriverMonthlyScoreRepository;
import com.ybs.demo.planning.Journey;
import com.ybs.demo.simulation.Bus;
import com.ybs.demo.simulation.BusRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates JPA entities and A* results into ground Prolog facts.
 *
 * <p>This is the only place the two halves of the system meet. Keeping it separate from
 * {@link PrologService} means the bridge stays generic (load, assert, solve) and everything
 * domain-specific about *what* gets asserted is here, where it can be read in one sitting.
 */
@Service
public class FactAssembler {

    /**
     * Stops where changing bus means crossing a pedestrian overbridge. Hand-listed for
     * the demo: these are the junctions on this network where the stops sit on opposite
     * carriageways of a divided road. Real accessibility data does not exist for YBS.
     */
    private static final Set<Long> OVERBRIDGE_STOPS = Set.of(6L, 9L, 5L);  // Hledan, Myaynigone, Hlaing

    private final PrologService prolog;
    private final BusRepository busRepository;
    private final DisruptionService disruptions;
    private final DriverMonthlyScoreRepository scoreRepository;
    private final NetworkService network;

    /**
     * Routes whose itinerary has already been asserted this request. Cleared by
     * {@link #resetWorld()}, which is what starts a request - a per-call set would only
     * deduplicate within one journey, and two options sharing a route are the normal case.
     */
    private final Set<String> servesAsserted = new HashSet<>();

    public FactAssembler(PrologService prolog,
                         BusRepository busRepository,
                         DisruptionService disruptions,
                         DriverMonthlyScoreRepository scoreRepository,
                         NetworkService network) {
        this.prolog = prolog;
        this.busRepository = busRepository;
        this.disruptions = disruptions;
        this.scoreRepository = scoreRepository;
        this.network = network;
    }

    /**
     * Seeds the engine with the world facts at startup.
     *
     * <p>Without this, {@code /api/prolog/query} answers differently depending on whether a
     * journey search happened to have run first, because that is what loads {@code
     * base_fare/2}. During a live demo the examiner types goals in whatever order they
     * like, so the engine must never depend on a hidden prior request.
     */
    @Order(4)  // after NetworkService has cached the routes
    @EventListener(ApplicationReadyEvent.class)
    public void seedAtStartup() {
        assertWorldFacts();
    }

    /**
     * The baseline every feature resets to: rules plus the standing world facts. The
     * engine is shared across features, so anything that wipes it must restore these -
     * otherwise {@code /api/prolog/query} goals like {@code fare(...)} start failing
     * depending on which page was visited last.
     */
    public void resetWorld() {
        prolog.resetToBaseTheory();
        servesAsserted.clear();
        assertWorldFacts();
    }

    /** Fares, plus any flood currently in force. */
    public void assertWorldFacts() {
        for (Route r : network.allRoutes()) {
            prolog.assertFact("base_fare('" + r.getCode() + "', " + r.getBaseFareKyats() + ")");
        }
        // Only floods that are active right now - see DisruptionService for why the
        // seeded historical incidents are deliberately not asserted here.
        for (Long stopId : disruptions.floodedStops()) {
            prolog.assertFact("flooded(s" + stopId + ")");
        }

        // Driver scores belong here too. They describe the world, not one request, and
        // every reset wipes them - so if only the ops page asserted them, a hand-typed
        // bonus_eligible/2 goal in the live console would answer "false" the moment
        // anything else had reset the theory. That looks exactly like a broken rule.
        assertDriverScores();
    }

    /** The rider's profile, as chosen by the toggles in the UI. */
    public void assertRider(String riderId, Set<String> flags) {
        for (String flag : flags) {
            // Only the five predicates the rule base knows about.
            switch (flag) {
                case "monk", "student", "verified_card", "pregnant", "needs_seat", "mobility_limited" ->
                        prolog.assertFact(flag + "(" + riderId + ")");
                default -> { /* ignore anything the rule base cannot use */ }
            }
        }
    }

    /**
     * Everything the constraint rules need to know about one candidate journey.
     * Crowding is taken from the buses currently running the routes this journey uses -
     * so flipping "needs a seat" is judged against the live simulation, not a fixture.
     */
    public void assertJourney(Journey journey) {
        String j = journey.id();
        prolog.assertFact("transfer_count(" + j + ", " + journey.transferCount() + ")");
        prolog.assertFact("predicted_crowding(" + j + ", " + crowdingOf(journey) + ")");

        // The three objectives A* traded off, so the rule base can state Pareto
        // dominance itself rather than only being told the answer. Minutes are rounded
        // to whole numbers: tuProlog compares integers exactly, and two journeys that
        // differ by a twentieth of a minute are not a real trade-off.
        prolog.assertFact("journey_stats(" + j + ", " + Math.round(journey.totalMinutes())
                + ", " + journey.totalFareKyats() + ", " + journey.transferCount() + ")");

        // One fact per RIDE leg, carrying the span the fare rules price on. Walking legs
        // are deliberately absent - they are free and have no route, and leaving them
        // out is what lets journey_fare/3 sum an empty list for an all-walking journey
        // instead of needing a special case.
        for (Journey.Leg leg : journey.legs()) {
            if ("ride".equals(leg.kind()) && leg.routeCode() != null) {
                prolog.assertFact("leg(" + j + ", '" + leg.routeCode() + "', "
                        + (leg.fullRoute() ? "full" : "part") + ")");
            }
        }
        assertServes(journey.routeCodes());

        for (Long stopId : journey.stopIds()) {
            prolog.assertFact("uses_segment(" + j + ", s" + stopId + ")");
            if (OVERBRIDGE_STOPS.contains(stopId) && journey.transferCount() > 0) {
                prolog.assertFact("uses_overbridge(" + j + ")");
            }
        }
    }

    /**
     * Which stops the routes in play call at, for the reachability rules.
     *
     * <p>Scoped to the routes the search actually proposed rather than all 118. The whole
     * network is 12,280 route-stop pairs, and asserting that on every request would cost
     * more than the search it is supporting - so {@code serves/2}, like {@code suitable/2},
     * answers about the last search. The rule base says so where it uses it.
     *
     * <p>Deduplicated across journeys, because two options sharing a route would otherwise
     * assert its whole itinerary twice and {@code setof/3} would still be right while
     * {@code count_solutions/2} quietly was not.
     */
    private void assertServes(List<String> routeCodes) {
        for (String code : routeCodes) {
            if (code == null || !servesAsserted.add(code)) continue;
            Route route = network.routeByCode(code);
            if (route == null) continue;
            for (RouteStop rs : network.itinerary(route.getId())) {
                prolog.assertFact("serves('" + code + "', s" + rs.getStop().getId() + ")");
            }
        }
    }

    /** Worst crowding across the buses serving any route this journey rides. */
    private String crowdingOf(Journey journey) {
        List<Bus> buses = busRepository.findAll();
        int worst = 0;
        for (Bus b : buses) {
            if (journey.routeCodes().contains(b.getRoute().getCode())) {
                worst = Math.max(worst, b.getCrowdingLevel());
            }
        }
        return switch (worst) {
            case 0 -> "free";
            case 1 -> "standing";
            default -> "full";
        };
    }

    /** Driver scores for the ops dashboard's bonus badge. */
    public void assertDriverScores() {
        for (DriverMonthlyScore s : scoreRepository.findAll()) {
            Long id = s.getDriver().getId();
            String month = "'" + s.getId().getMonthKey() + "'";
            prolog.assertFact("headway_score(d" + id + ", " + month + ", " + s.getHeadwayScore() + ")");
            prolog.assertFact("harsh_events(d" + id + ", " + month + ", " + s.getHarshEvents() + ")");
            if (s.getUpheldComplaints() != null && s.getUpheldComplaints() > 0) {
                prolog.assertFact("upheld_complaint(d" + id + ", " + month + ")");
            }
        }
    }
}
