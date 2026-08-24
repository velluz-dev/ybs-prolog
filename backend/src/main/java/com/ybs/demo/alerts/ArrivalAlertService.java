package com.ybs.demo.alerts;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;
import com.ybs.demo.shared.GeoUtils;
import com.ybs.demo.simulation.BusSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fires once when the rider's bus gets within {@link #GEOFENCE_METRES} of the stop they
 * chose to get off at.
 *
 * <p>400 m is roughly one long block, or 30-60 seconds of running time in traffic - far
 * enough ahead that a rider standing in a packed bus can start pushing towards the door,
 * close enough that they do not stand up four stops early. It is the same radius the stop
 * graph uses for a walkable transfer, so the demo has one consistent notion of "near".
 *
 * <p>The fired flag is what makes it fire <b>once</b>: without it the alert would re-trigger
 * on every one of the 1 Hz ticks the bus spends inside the circle.
 */
@Service
public class ArrivalAlertService {
    private static final Logger log = LoggerFactory.getLogger(ArrivalAlertService.class);

    public static final double GEOFENCE_METRES = 400.0;

    private final NetworkService network;
    private final BusSimulator simulator;

    private volatile Long watchedBusId;
    private volatile Long alightStopId;
    private final AtomicBoolean fired = new AtomicBoolean(false);

    public ArrivalAlertService(NetworkService network, BusSimulator simulator) {
        this.network = network;
        this.simulator = simulator;
    }

    /** Begin watching: the rider is on {@code busId} and getting off at {@code stopId}. */
    public void startTrip(Long busId, Long stopId) {
        this.watchedBusId = busId;
        this.alightStopId = stopId;
        this.fired.set(false);
        Stop s = network.stop(stopId);
        log.info("Trip started: watching bus {} for arrival at {} ({})",
                busId, stopId, s == null ? "?" : s.getNameEn());
    }

    public void cancelTrip() {
        watchedBusId = null;
        alightStopId = null;
        fired.set(false);
    }

    /**
     * Current alert state. Polled by the frontend alongside the bus positions.
     * Returns {@code triggered=true} exactly once per trip.
     */
    public Map<String, Object> poll() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("watching", watchedBusId != null);
        out.put("triggered", false);

        if (watchedBusId == null || alightStopId == null) {
            return out;
        }
        Stop stop = network.stop(alightStopId);
        if (stop == null) {
            return out;
        }

        Map<String, Object> bus = simulator.snapshot().stream()
                .filter(b -> watchedBusId.equals(b.get("id")))
                .findFirst().orElse(null);
        if (bus == null) {
            return out;
        }

        double metres = GeoUtils.haversineKm(
                (Double) bus.get("lat"), (Double) bus.get("lon"),
                stop.getLat(), stop.getLon()) * 1000.0;

        out.put("distanceMetres", Math.round(metres));
        out.put("stopId", stop.getId());
        out.put("stopNameMy", stop.getNameMy());
        out.put("stopNameEn", stop.getNameEn());
        out.put("landmarkPhotoUrl", stop.getLandmarkPhotoUrl());

        if (metres <= GEOFENCE_METRES && fired.compareAndSet(false, true)) {
            out.put("triggered", true);
            out.put("clips", clipSequence(stop.getId()));
            log.info("ARRIVAL ALERT: bus {} is {}m from {} - firing once",
                    watchedBusId, Math.round(metres), stop.getNameEn());
        }
        return out;
    }

    /**
     * The clip sequence to play, as base names without an extension.
     *
     * <p>Concatenating {@code prefix + stop_<id> + suffix} rather than synthesising speech:
     * pre-recorded Burmese is more reliable and sounds far better than any TTS voice
     * available offline. The player tries {@code .mp3} first and falls back to {@code .wav},
     * so dropping in phone recordings later needs no code change.
     */
    public List<String> clipSequence(Long stopId) {
        return List.of("prefix", "stop_" + stopId, "suffix");
    }
}
