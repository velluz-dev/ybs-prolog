package com.ybs.demo;

import com.ybs.demo.eta.DwellModel;
import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.simulation.ArrivalPredictor;
import com.ybs.demo.simulation.Bus;
import com.ybs.demo.simulation.BusSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the three things the simulation now models: driving, waiting at stops, and
 * predicted arrival times.
 */
@SpringBootTest
class SimulationTest {

    @Autowired DwellModel dwellModel;
    @Autowired EtaService eta;
    @Autowired NetworkService network;
    @Autowired BusSimulator simulator;
    @Autowired ArrivalPredictor predictor;

    /** Stop 6 is Hledan, served by routes 36 and 43. Stop 3 (Mayangone) is route 36 only. */
    private static final Long HLEDAN = 6L;
    private static final Long MAYANGONE = 3L;

    @Test
    void fullerBusWaitsLonger() {
        int free = dwellModel.dwellSeconds(MAYANGONE, 0, false);
        int standing = dwellModel.dwellSeconds(MAYANGONE, 1, false);
        int full = dwellModel.dwellSeconds(MAYANGONE, 2, false);

        assertTrue(free < standing, "standing room should dwell longer than seats free");
        assertTrue(standing < full, "a full bus should dwell longest");
    }

    @Test
    void interchangeStopsWaitLongerThanOrdinaryOnes() {
        // Derived from the data, not hardcoded: Hledan is served by two routes, Mayangone by one.
        assertTrue(network.routeCountAt(HLEDAN) > 1, "Hledan should be an interchange");
        assertEquals(1, network.routeCountAt(MAYANGONE), "Mayangone should be single-route");

        assertTrue(dwellModel.dwellSeconds(HLEDAN, 1, false) > dwellModel.dwellSeconds(MAYANGONE, 1, false),
                "an interchange should dwell longer than an ordinary stop at equal crowding");
    }

    @Test
    void terminusTakesALayoverRegardlessOfCrowding() {
        assertEquals(DwellModel.LAYOVER_SECONDS, dwellModel.dwellSeconds(MAYANGONE, 0, true));
        assertEquals(DwellModel.LAYOVER_SECONDS, dwellModel.dwellSeconds(HLEDAN, 2, true));
    }

    /**
     * The regression this whole change is most likely to reintroduce. Dwell used to be a
     * multiplier on driving time; now it is real waiting. If both were applied, a bus would
     * take driving x 1.25 PLUS a dwell to cover one segment, and every bus would run slow.
     *
     * <p>So: the time to cross one segment while never stopping must equal the traffic-scaled
     * driving time alone, with no crowding multiplier folded in.
     */
    @Test
    void drivingTimeDoesNotSecretlyIncludeDwell() {
        Bus bus = simulator.fleet().stream()
                .filter(b -> !b.isDwelling())
                .filter(b -> b.getCrowdingLevel() > 0)   // a crowding multiplier would be visible
                .findFirst().orElseThrow(() -> new AssertionError("no crowded bus is currently driving"));

        String bucket = EtaService.currentBucket();
        int index = bus.getCurrentSegmentIndex();

        double baseMinutes = network.baseSegmentMinutes(bus.getRoute().getId(), index);
        var itinerary = network.itinerary(bus.getRoute().getId());
        String segmentId = EtaService.segmentId(bus.getRoute().getCode(),
                itinerary.get(index).getStop().getId(),
                itinerary.get(index + 1).getStop().getId());

        double expected = (1.0 - bus.getProgressAlongSegment())
                * baseMinutes * 60.0 * eta.trafficFactor(segmentId, bucket);

        assertEquals(expected, simulator.secondsToNextStop(bus, bucket), 1e-6,
                "driving time must be traffic-scaled travel only. If this fails by roughly "
                        + "10-25%, the old dwellPenalty crowding multiplier has been "
                        + "reintroduced and dwell is being charged twice.");
    }

    @Test
    void busEventuallyStopsAndWaits() {
        // 60x makes a tick a simulated minute, so a stop is reached within a few ticks.
        simulator.setSpeedMultiplier(60);
        boolean sawDwelling = false;
        for (int i = 0; i < 120 && !sawDwelling; i++) {
            simulator.tick();
            sawDwelling = simulator.fleet().stream().anyMatch(Bus::isDwelling);
        }
        simulator.setSpeedMultiplier(1);
        assertTrue(sawDwelling, "no bus ever stopped at a stop");
    }

    @Test
    void dwellingBusHoldsItsPosition() {
        simulator.setSpeedMultiplier(60);
        Bus dwelling = null;
        for (int i = 0; i < 120 && dwelling == null; i++) {
            simulator.tick();
            dwelling = simulator.fleet().stream().filter(Bus::isDwelling).findFirst().orElse(null);
        }
        simulator.setSpeedMultiplier(1);
        if (dwelling == null) {
            return;   // covered by busEventuallyStopsAndWaits
        }

        double lat = dwelling.getCurrentLat();
        double lon = dwelling.getCurrentLon();
        Long id = dwelling.getId();

        simulator.setSpeedMultiplier(1);
        simulator.tick();   // one simulated second - far less than any dwell

        Bus after = simulator.fleet().stream().filter(b -> b.getId().equals(id)).findFirst().orElseThrow();
        if (after.isDwelling()) {
            assertEquals(lat, after.getCurrentLat(), 1e-9, "a waiting bus must not move");
            assertEquals(lon, after.getCurrentLon(), 1e-9, "a waiting bus must not move");
        }
    }

    @Test
    void arrivalsForABusIncreaseDownTheRoute() {
        Bus bus = simulator.fleet().get(0);
        List<ArrivalPredictor.Arrival> arrivals = predictor.forBus(bus.getId());

        assertFalse(arrivals.isEmpty(), "a running bus should have stops ahead of it");
        for (int i = 1; i < arrivals.size(); i++) {
            assertTrue(arrivals.get(i).etaMinutes() > arrivals.get(i - 1).etaMinutes(),
                    "arrival times must increase along the route, but stop "
                            + arrivals.get(i).stopNameEn() + " was not later than the one before");
        }
    }

    @Test
    void everyArrivalCarriesAConfidenceBand() {
        for (ArrivalPredictor.Arrival a : predictor.forBus(simulator.fleet().get(0).getId())) {
            assertTrue(a.etaLowerBound() <= a.etaMinutes(), "lower bound above the estimate");
            assertTrue(a.etaUpperBound() >= a.etaMinutes(), "upper bound below the estimate");
        }
    }

    @Test
    void stopBoardIsSortedSoonestFirst() {
        List<ArrivalPredictor.Arrival> due = predictor.forStop(HLEDAN);
        for (int i = 1; i < due.size(); i++) {
            assertTrue(due.get(i).etaMinutes() >= due.get(i - 1).etaMinutes(),
                    "the stop board must be sorted soonest first");
        }
        // Each bus may appear at most once on a board.
        long distinctBuses = due.stream().map(ArrivalPredictor.Arrival::busId).distinct().count();
        assertEquals(due.size(), distinctBuses, "a bus should appear at most once per stop board");
    }
}
