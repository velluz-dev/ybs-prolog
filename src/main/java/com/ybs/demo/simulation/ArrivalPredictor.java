package com.ybs.demo.simulation;

import com.ybs.demo.eta.DwellModel;
import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.network.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Predicts when each bus reaches each stop ahead of it.
 *
 * <p>The method is a forward walk over the same model the simulator itself runs on: take
 * whatever the bus is doing right now (mid-segment, or standing at a stop), then add, stop by
 * stop, the traffic-scaled driving time for each segment and the dwell at each stop. Because
 * both use {@link EtaService} and {@link DwellModel}, a prediction and the bus it describes
 * cannot drift apart - there is one model, not a simulator and a separate estimator.
 *
 * <p>Every prediction carries the <b>same +/-25% band</b> as a planned journey, produced by
 * {@link EtaService#estimate}. That is the point of the uncertainty component: a rider is
 * told "4 to 7 minutes", not "5.4 minutes", because the model does not know better than that.
 *
 * <p>Predictions stop at the end of the route rather than looping. A bus that has passed your
 * stop is not coming back within any horizon a rider cares about, and pretending otherwise
 * would put a nonsense 70-minute "arrival" on the board.
 */
@Service
public class ArrivalPredictor {
    private static final Logger log = LoggerFactory.getLogger(ArrivalPredictor.class);

    private final NetworkService network;
    private final EtaService eta;
    private final DwellModel dwellModel;
    private final BusSimulator simulator;

    public ArrivalPredictor(NetworkService network,
                            EtaService eta,
                            DwellModel dwellModel,
                            BusSimulator simulator) {
        this.network = network;
        this.eta = eta;
        this.dwellModel = dwellModel;
        this.simulator = simulator;
    }

    /**
     * One predicted arrival.
     *
     * @param sequence position of the stop on the route, so a caller can tell direction
     */
    public record Arrival(Long busId, String routeCode, Long stopId, String stopNameMy,
                          String stopNameEn, int sequence, double etaMinutes,
                          double etaLowerBound, double etaUpperBound, String bucket) {}

    /** Every remaining stop for one bus, in running order. */
    public List<Arrival> forBus(Long busId) {
        Bus bus = simulator.fleet().stream()
                .filter(b -> b.getId().equals(busId))
                .findFirst().orElse(null);
        return bus == null ? List.of() : walkForward(bus);
    }

    /**
     * Every bus due at one stop, soonest first - the "when's my bus" board.
     * A bus appears only if the stop is still ahead of it on its current run.
     */
    public List<Arrival> forStop(Long stopId) {
        List<Arrival> due = new ArrayList<>();
        for (Bus bus : simulator.fleet()) {
            for (Arrival a : walkForward(bus)) {
                if (a.stopId().equals(stopId)) {
                    due.add(a);
                    break;   // first time this bus reaches the stop is the one that matters
                }
            }
        }
        due.sort(Comparator.comparingDouble(Arrival::etaMinutes));
        log.debug("{} bus(es) due at stop {}", due.size(), stopId);
        return due;
    }

    /**
     * Accumulates time from the bus's current state to the end of its route.
     *
     * <p>The first entry costs whatever is left of the current move - remaining dwell plus a
     * whole segment if it is standing at a stop, or the unfinished part of the segment if it
     * is driving. After that each stop costs one dwell plus one segment.
     */
    private List<Arrival> walkForward(Bus bus) {
        String bucket = EtaService.currentBucket();
        Long routeId = bus.getRoute().getId();
        List<RouteStop> itinerary = network.itinerary(routeId);
        int segments = network.segmentCount(routeId);
        List<Arrival> out = new ArrayList<>();
        if (segments == 0) {
            return out;
        }

        int index = bus.getCurrentSegmentIndex();
        if (index < 0 || index >= segments) {
            index = 0;
        }

        // Time to reach the very next stop, taken straight from the simulator so the
        // prediction and the moving marker cannot disagree.
        double minutes = simulator.secondsToNextStop(bus, bucket) / 60.0;

        for (int i = index; i < segments; i++) {
            RouteStop arriving = itinerary.get(i + 1);
            Stop stop = arriving.getStop();
            boolean terminus = (i + 1) == segments;

            if (i > index) {
                // Dwell at the previous stop, then drive this segment.
                Stop previous = itinerary.get(i).getStop();
                minutes += dwellModel.dwellMinutes(previous.getId(), bus.getCrowdingLevel(), false);
                minutes += drivingMinutes(bus, i, bucket);
            }

            EtaService.Estimate estimate = eta.estimate(minutes, bucket);
            out.add(new Arrival(bus.getId(), bus.getRoute().getCode(), stop.getId(),
                    stop.getNameMy(), stop.getNameEn(), arriving.getSequence(),
                    estimate.etaMinutes(), estimate.etaLowerBound(), estimate.etaUpperBound(),
                    bucket));

            if (terminus) {
                break;
            }
        }
        return out;
    }

    private double drivingMinutes(Bus bus, int index, String bucket) {
        Long routeId = bus.getRoute().getId();
        double baseMinutes = network.baseSegmentMinutes(routeId, index);
        if (baseMinutes <= 0) {
            baseMinutes = 1;
        }
        List<RouteStop> itinerary = network.itinerary(routeId);
        String segmentId = EtaService.segmentId(bus.getRoute().getCode(),
                itinerary.get(index).getStop().getId(),
                itinerary.get(index + 1).getStop().getId());
        return eta.segmentMinutes(baseMinutes, segmentId, bucket);
    }
}
