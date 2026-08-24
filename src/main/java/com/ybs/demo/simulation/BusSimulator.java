package com.ybs.demo.simulation;

import com.ybs.demo.eta.DwellModel;
import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.network.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives every bus once a second through two alternating states: <b>DRIVING</b> along a
 * segment, then <b>DWELLING</b> at the stop it reaches.
 *
 * <p>Driving time for a segment is its free-flow time scaled by the traffic prior:
 * <pre>segmentSeconds = baseMinutes x 60 x trafficFactor(segment, bucket)</pre>
 *
 * <p><b>There is deliberately no dwell multiplier in that formula.</b> An earlier version
 * multiplied driving time by 1.0-1.25 depending on crowding, as a stand-in for time spent at
 * stops. Now that buses actually stop, keeping that factor would charge dwell twice - once
 * smeared into the drive and once at the stop - and every bus would run visibly slow.
 * Crowding still lengthens dwell; it just does so in {@link DwellModel}, where it belongs.
 *
 * <p>The tick spends a <b>time budget</b> rather than advancing a fixed fraction, because at
 * 60x one tick is a simulated minute and can cross several segments and several dwells.
 *
 * <p>Positions live in memory; the database copy is never updated.
 */
@Service
public class BusSimulator {
    private static final Logger log = LoggerFactory.getLogger(BusSimulator.class);

    private static final double TICK_SECONDS = 1.0;

    /** Guards against a pathological route of zero-length segments spinning the budget loop. */
    private static final int MAX_STEPS_PER_TICK = 500;

    private final NetworkService network;
    private final EtaService eta;
    private final DwellModel dwellModel;
    private final BusRepository busRepository;

    /** 1x, 10x or 60x - the demo needs to fast-forward a bus into the geofence on stage. */
    private final AtomicInteger speedMultiplier = new AtomicInteger(1);

    private final List<Bus> buses = new ArrayList<>();

    public BusSimulator(NetworkService network,
                        EtaService eta,
                        DwellModel dwellModel,
                        BusRepository busRepository) {
        this.network = network;
        this.eta = eta;
        this.dwellModel = dwellModel;
        this.busRepository = busRepository;
    }

    /** See {@link NetworkService#load()} for why this is not @PostConstruct. */
    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        buses.addAll(busRepository.findAll());
        log.info("Simulating {} buses", buses.size());
    }

    public int getSpeedMultiplier() {
        return speedMultiplier.get();
    }

    public void setSpeedMultiplier(int multiplier) {
        speedMultiplier.set(multiplier);
        log.info("Simulation speed set to {}x", multiplier);
    }

    /** Live copy of the fleet. Callers must not mutate the returned buses. */
    public List<Bus> fleet() {
        synchronized (buses) {
            return new ArrayList<>(buses);
        }
    }

    public List<Map<String, Object>> snapshot() {
        String bucket = EtaService.currentBucket();
        List<Map<String, Object>> out = new ArrayList<>();
        synchronized (buses) {
            for (Bus b : buses) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", b.getId());
                m.put("routeId", b.getRoute().getId());
                m.put("routeCode", b.getRoute().getCode());
                m.put("lat", b.getCurrentLat());
                m.put("lon", b.getCurrentLon());
                m.put("segmentIndex", b.getCurrentSegmentIndex());
                m.put("progress", b.getProgressAlongSegment());
                m.put("crowdingLevel", b.getCrowdingLevel());
                m.put("state", b.getState());
                m.put("dwellSecondsRemaining", Math.round(b.getDwellSecondsRemaining()));

                Stop next = nextStop(b);
                if (next != null) {
                    m.put("nextStopId", next.getId());
                    m.put("nextStopNameMy", next.getNameMy());
                    m.put("nextStopNameEn", next.getNameEn());
                    m.put("minutesToNextStop", round(secondsToNextStop(b, bucket) / 60.0));
                }
                out.add(m);
            }
        }
        return out;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        String bucket = EtaService.currentBucket();
        int speed = speedMultiplier.get();
        synchronized (buses) {
            for (Bus bus : buses) {
                advance(bus, bucket, speed);
            }
        }
    }

    /**
     * Spends this tick's simulated seconds on the bus, alternating between waiting out a
     * dwell and covering ground, until the budget runs out.
     */
    private void advance(Bus bus, String bucket, int speed) {
        Long routeId = bus.getRoute().getId();
        int segments = network.segmentCount(routeId);
        if (segments == 0) {
            return;
        }

        double budget = TICK_SECONDS * speed;
        int steps = 0;

        while (budget > 0 && steps++ < MAX_STEPS_PER_TICK) {
            if (bus.isDwelling()) {
                double spent = Math.min(budget, bus.getDwellSecondsRemaining());
                bus.setDwellSecondsRemaining(bus.getDwellSecondsRemaining() - spent);
                budget -= spent;
                if (bus.isDwelling()) {
                    return;   // still standing at the stop when the budget ran out
                }
                continue;     // dwell finished; spend what is left driving
            }

            int index = clampIndex(bus.getCurrentSegmentIndex(), segments);
            double segmentSeconds = segmentSeconds(bus, index, bucket);
            double secondsLeft = (1.0 - bus.getProgressAlongSegment()) * segmentSeconds;

            if (budget < secondsLeft) {
                // Still mid-segment: move partway and stop here for this tick.
                double progress = bus.getProgressAlongSegment() + budget / segmentSeconds;
                setPositionAlongSegment(bus, index, progress);
                return;
            }

            // Reached the next stop. Park exactly on it and start dwelling.
            budget -= secondsLeft;
            int arrivedAt = index + 1;
            List<RouteStop> itinerary = network.itinerary(routeId);
            Stop stop = itinerary.get(arrivedAt).getStop();
            boolean terminus = arrivedAt == segments;

            bus.setCurrentLat(stop.getLat());
            bus.setCurrentLon(stop.getLon());
            bus.setProgressAlongSegment(0.0);
            // The terminus is also the start of the next loop, so wrap the index there.
            bus.setCurrentSegmentIndex(terminus ? 0 : arrivedAt);
            bus.setDwellSecondsRemaining(
                    dwellModel.dwellSeconds(stop.getId(), bus.getCrowdingLevel(), terminus));
        }
    }

    /** Free-flow time for the segment, scaled by the traffic prior. No dwell in here. */
    private double segmentSeconds(Bus bus, int index, String bucket) {
        Long routeId = bus.getRoute().getId();
        double baseMinutes = network.baseSegmentMinutes(routeId, index);
        if (baseMinutes <= 0) {
            baseMinutes = 1;   // guard against a zero-length seeded segment
        }
        List<RouteStop> itinerary = network.itinerary(routeId);
        String segmentId = EtaService.segmentId(bus.getRoute().getCode(),
                itinerary.get(index).getStop().getId(),
                itinerary.get(index + 1).getStop().getId());
        return baseMinutes * 60.0 * eta.trafficFactor(segmentId, bucket);
    }

    /** Seconds until this bus reaches its next stop, including any dwell it is serving. */
    public double secondsToNextStop(Bus bus, String bucket) {
        int segments = network.segmentCount(bus.getRoute().getId());
        if (segments == 0) {
            return 0;
        }
        int index = clampIndex(bus.getCurrentSegmentIndex(), segments);
        if (bus.isDwelling()) {
            // It is standing at a stop; "next" is the one after this one.
            return bus.getDwellSecondsRemaining() + segmentSeconds(bus, index, bucket);
        }
        return (1.0 - bus.getProgressAlongSegment()) * segmentSeconds(bus, index, bucket);
    }

    /** The stop this bus is heading for. */
    public Stop nextStop(Bus bus) {
        Long routeId = bus.getRoute().getId();
        int segments = network.segmentCount(routeId);
        if (segments == 0) {
            return null;
        }
        int index = clampIndex(bus.getCurrentSegmentIndex(), segments);
        return network.itinerary(routeId).get(index + 1).getStop();
    }

    private void setPositionAlongSegment(Bus bus, int index, double progress) {
        List<RouteStop> itinerary = network.itinerary(bus.getRoute().getId());
        Stop from = itinerary.get(index).getStop();
        Stop to = itinerary.get(index + 1).getStop();
        bus.setCurrentSegmentIndex(index);
        bus.setProgressAlongSegment(progress);
        bus.setCurrentLat(lerp(from.getLat(), to.getLat(), progress));
        bus.setCurrentLon(lerp(from.getLon(), to.getLon(), progress));
    }

    private static int clampIndex(int index, int segments) {
        return (index < 0 || index >= segments) ? 0 : index;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
