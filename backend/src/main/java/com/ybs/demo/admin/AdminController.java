package com.ybs.demo.admin;

import com.ybs.demo.eta.TrafficFactor;
import com.ybs.demo.eta.TrafficFactorRepository;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Route;
import com.ybs.demo.network.RouteRepository;
import com.ybs.demo.network.RouteStop;
import com.ybs.demo.network.RouteStopId;
import com.ybs.demo.network.RouteStopRepository;
import com.ybs.demo.network.Stop;
import com.ybs.demo.network.StopRepository;
import com.ybs.demo.ops.Driver;
import com.ybs.demo.ops.DriverMonthlyScore;
import com.ybs.demo.ops.DriverMonthlyScoreId;
import com.ybs.demo.ops.DriverMonthlyScoreRepository;
import com.ybs.demo.ops.DriverRepository;
import com.ybs.demo.ops.Incident;
import com.ybs.demo.ops.IncidentRepository;
import com.ybs.demo.ops.IncidentType;
import com.ybs.demo.simulation.Bus;
import com.ybs.demo.simulation.BusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data entry for every table the demo reasons over.
 *
 * <p>Writes go to the in-memory H2, which is reseeded from {@code data.sql} on every
 * startup - so a change made here is live immediately but gone on restart. That is the
 * point of {@link SeedExportService}: fill data in, export a regenerated {@code data.sql},
 * and drop it into {@code backend/src/main/resources/} to make it permanent and shareable.
 * The alternative, a file-backed database, would stop the demo resetting clean between runs.
 *
 * <p>Every write calls {@link NetworkReloader}, because the map, the planner and the
 * simulation all read from caches built at startup and would otherwise ignore the change.
 *
 * <p>No authentication, deliberately - the brief rules it out, and this listens on
 * localhost only.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final StopRepository stops;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final TrafficFactorRepository traffic;
    private final DriverRepository drivers;
    private final DriverMonthlyScoreRepository scores;
    private final BusRepository buses;
    private final IncidentRepository incidents;
    private final NetworkService network;
    private final NetworkReloader reloader;
    private final SeedExportService exporter;

    public AdminController(StopRepository stops, RouteRepository routes,
                           RouteStopRepository routeStops, TrafficFactorRepository traffic,
                           DriverRepository drivers, DriverMonthlyScoreRepository scores,
                           BusRepository buses, IncidentRepository incidents,
                           NetworkService network, NetworkReloader reloader,
                           SeedExportService exporter) {
        this.stops = stops;
        this.routes = routes;
        this.routeStops = routeStops;
        this.traffic = traffic;
        this.drivers = drivers;
        this.scores = scores;
        this.buses = buses;
        this.incidents = incidents;
        this.network = network;
        this.reloader = reloader;
        this.exporter = exporter;
    }

    /** Row counts, so the admin page can show what is in the database right now. */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stops", stops.count());
        out.put("routes", routes.count());
        out.put("routeStops", routeStops.count());
        out.put("trafficFactors", traffic.count());
        out.put("drivers", drivers.count());
        out.put("driverScores", scores.count());
        out.put("buses", buses.count());
        out.put("incidents", incidents.count());
        return out;
    }

    // ------------------------------------------------------------------ stops --

    @PostMapping("/stops")
    @Transactional
    public Map<String, Object> saveStop(@RequestBody Map<String, Object> body) {
        Stop s = id(body) == null ? new Stop() : stops.findById(id(body)).orElse(new Stop());
        if (id(body) != null) s.setId(id(body));
        s.setNameMy(str(body, "nameMy"));
        s.setNameEn(str(body, "nameEn"));
        s.setLat(dbl(body, "lat"));
        s.setLon(dbl(body, "lon"));
        s.setLandmarkPhotoUrl(str(body, "landmarkPhotoUrl"));
        Stop saved = stops.save(s);
        reloader.reload();
        log.info("Admin saved stop {} ({})", saved.getId(), saved.getNameEn());
        return Map.of("id", saved.getId(), "nameEn", saved.getNameEn());
    }

    @DeleteMapping("/stops/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteStop(@PathVariable Long id) {
        // A stop still on a route cannot be removed without silently breaking that
        // route's itinerary, so refuse and say which routes are in the way.
        List<String> used = new ArrayList<>();
        for (Route r : routes.findAll()) {
            boolean onRoute = routeStops.findByIdRouteIdOrderByIdSequenceNoAsc(r.getId()).stream()
                    .anyMatch(rs -> rs.getStop().getId().equals(id));
            if (onRoute) used.add(r.getCode());
        }
        if (!used.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "stop is still used by route(s): " + String.join(", ", used),
                    "routes", used));
        }
        stops.deleteById(id);
        reloader.reload();
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ----------------------------------------------------------------- routes --

    @PostMapping("/routes")
    @Transactional
    public Map<String, Object> saveRoute(@RequestBody Map<String, Object> body) {
        Route r = id(body) == null ? new Route() : routes.findById(id(body)).orElse(new Route());
        if (id(body) != null) r.setId(id(body));
        r.setCode(str(body, "code"));
        r.setNameMy(str(body, "nameMy"));
        r.setBaseFareKyats(intOf(body, "baseFareKyats", 200));
        Route saved = routes.save(r);
        reloader.reload();
        log.info("Admin saved route {} ({})", saved.getId(), saved.getCode());
        return Map.of("id", saved.getId(), "code", saved.getCode());
    }

    @DeleteMapping("/routes/{id}")
    @Transactional
    public Map<String, Object> deleteRoute(@PathVariable Long id) {
        routeStops.deleteAll(routeStops.findByIdRouteIdOrderByIdSequenceNoAsc(id));
        buses.findAll().stream()
                .filter(b -> b.getRoute() != null && b.getRoute().getId().equals(id))
                .forEach(buses::delete);
        routes.deleteById(id);
        reloader.reload();
        return Map.of("deleted", id);
    }

    /**
     * Replaces a route's whole itinerary in one call.
     *
     * <p>Whole-list rather than per-stop, because sequence numbers and the cumulative
     * running times only make sense as a set - editing one row at a time would leave the
     * route inconsistent between saves.
     */
    @PostMapping("/routes/{routeId}/stops")
    @Transactional
    public Map<String, Object> setItinerary(@PathVariable Long routeId,
                                            @RequestBody List<Map<String, Object>> body) {
        Route route = routes.findById(routeId).orElseThrow();
        routeStops.deleteAll(routeStops.findByIdRouteIdOrderByIdSequenceNoAsc(routeId));
        routeStops.flush();

        int seq = 0;
        for (Map<String, Object> row : body) {
            RouteStop rs = new RouteStop();
            rs.setId(new RouteStopId(routeId, seq));
            rs.setRoute(route);
            rs.setStop(stops.findById(lng(row, "stopId")).orElseThrow());
            rs.setCumulativeMinutes(intOf(row, "cumulativeMinutes", seq * 3));
            routeStops.save(rs);
            seq++;
        }
        reloader.reload();
        log.info("Admin set itinerary for route {}: {} stops", route.getCode(), seq);
        return Map.of("routeId", routeId, "stops", seq);
    }

    // --------------------------------------------------------- traffic prior --

    @GetMapping("/traffic")
    public List<TrafficFactor> listTraffic() {
        return traffic.findAll();
    }

    @PostMapping("/traffic")
    @Transactional
    public Map<String, Object> saveTraffic(@RequestBody Map<String, Object> body) {
        TrafficFactor t = traffic.findBySegmentIdAndBucket(str(body, "segmentId"), str(body, "bucket"))
                .orElseGet(TrafficFactor::new);
        t.setSegmentId(str(body, "segmentId"));
        t.setBucket(str(body, "bucket"));
        t.setFactor(dbl(body, "factor"));
        TrafficFactor saved = traffic.save(t);
        reloader.reload();   // drops the ETA cache so the new factor is used at once
        return Map.of("id", saved.getId(), "segmentId", saved.getSegmentId(),
                "bucket", saved.getBucket(), "factor", saved.getFactor());
    }

    @DeleteMapping("/traffic/{id}")
    @Transactional
    public Map<String, Object> deleteTraffic(@PathVariable Long id) {
        traffic.deleteById(id);
        reloader.reload();
        return Map.of("deleted", id);
    }

    // ---------------------------------------------------- drivers and scores --

    @PostMapping("/drivers")
    @Transactional
    public Map<String, Object> saveDriver(@RequestBody Map<String, Object> body) {
        Driver d = id(body) == null ? new Driver() : drivers.findById(id(body)).orElse(new Driver());
        if (id(body) != null) d.setId(id(body));
        d.setName(str(body, "name"));
        Long routeId = lng(body, "routeId");
        d.setRoute(routeId == null ? null : routes.findById(routeId).orElse(null));
        Driver saved = drivers.save(d);
        return Map.of("id", saved.getId(), "name", saved.getName());
    }

    /**
     * Saves a driver's month. {@code bonusEligible} is deliberately forced to false:
     * the ops dashboard recomputes it from the Prolog rules on every request, so storing
     * a "true" here would be a value nothing reads and everything contradicts.
     */
    @PostMapping("/scores")
    @Transactional
    public Map<String, Object> saveScore(@RequestBody Map<String, Object> body) {
        Long driverId = lng(body, "driverId");
        String month = str(body, "monthKey");
        DriverMonthlyScoreId key = new DriverMonthlyScoreId(driverId, month);
        DriverMonthlyScore s = scores.findById(key).orElseGet(DriverMonthlyScore::new);
        s.setId(key);
        s.setDriver(drivers.findById(driverId).orElseThrow());
        s.setHeadwayScore(intOf(body, "headwayScore", 0));
        s.setHarshEvents(intOf(body, "harshEvents", 0));
        s.setUpheldComplaints(intOf(body, "upheldComplaints", 0));
        s.setBonusEligible(false);
        scores.save(s);
        return Map.of("driverId", driverId, "monthKey", month);
    }

    @DeleteMapping("/drivers/{id}")
    @Transactional
    public Map<String, Object> deleteDriver(@PathVariable Long id) {
        scores.findAll().stream()
                .filter(s -> s.getDriver() != null && s.getDriver().getId().equals(id))
                .forEach(scores::delete);
        drivers.deleteById(id);
        return Map.of("deleted", id);
    }

    // --------------------------------------------------- buses and incidents --

    @PostMapping("/buses")
    @Transactional
    public Map<String, Object> saveBus(@RequestBody Map<String, Object> body) {
        Bus b = id(body) == null ? new Bus() : buses.findById(id(body)).orElse(new Bus());
        if (id(body) != null) b.setId(id(body));
        Route route = routes.findById(lng(body, "routeId")).orElseThrow();
        b.setRoute(route);
        b.setCrowdingLevel(intOf(body, "crowdingLevel", 0));

        // Start the bus on its route rather than trusting typed coordinates: a bus placed
        // off its own line would jump the moment the simulator advanced it.
        int index = intOf(body, "currentSegmentIndex", 0);
        var itinerary = network.itinerary(route.getId());
        if (!itinerary.isEmpty()) {
            index = Math.min(index, Math.max(0, itinerary.size() - 2));
            Stop at = itinerary.get(index).getStop();
            b.setCurrentLat(at.getLat());
            b.setCurrentLon(at.getLon());
        } else {
            b.setCurrentLat(dbl(body, "currentLat"));
            b.setCurrentLon(dbl(body, "currentLon"));
        }
        b.setCurrentSegmentIndex(index);
        b.setProgressAlongSegment(0.0);

        Bus saved = buses.save(b);
        reloader.reload();
        return Map.of("id", saved.getId(), "routeCode", route.getCode());
    }

    @DeleteMapping("/buses/{id}")
    @Transactional
    public Map<String, Object> deleteBus(@PathVariable Long id) {
        buses.deleteById(id);
        reloader.reload();
        return Map.of("deleted", id);
    }

    @PostMapping("/incidents")
    @Transactional
    public Map<String, Object> saveIncident(@RequestBody Map<String, Object> body) {
        Incident i = id(body) == null ? new Incident() : incidents.findById(id(body)).orElse(new Incident());
        if (id(body) != null) i.setId(id(body));
        i.setType(IncidentType.valueOf(str(body, "type")));
        i.setRouteId(lng(body, "routeId"));
        Long stopId = lng(body, "stopId");
        i.setStop(stopId == null ? null : stops.findById(stopId).orElse(null));
        String ts = str(body, "timestamp");
        i.setTimestamp(ts == null || ts.isBlank() ? LocalDateTime.now() : LocalDateTime.parse(ts));
        Incident saved = incidents.save(i);
        return Map.of("id", saved.getId(), "type", saved.getType().name());
    }

    @DeleteMapping("/incidents/{id}")
    @Transactional
    public Map<String, Object> deleteIncident(@PathVariable Long id) {
        incidents.deleteById(id);
        return Map.of("deleted", id);
    }

    // ----------------------------------------------------------------- export --

    /** Regenerates data.sql from whatever is in the database right now. */
    @GetMapping(value = "/export", produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> export() {
        String sql = exporter.exportSeed();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"data.sql\"")
                .body(sql);
    }

    // ------------------------------------------------------------- coercions --
    // Values arrive from a JSON form where every field may be absent, blank or a string.

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v).trim();
    }

    private static Long id(Map<String, Object> m) {
        return lng(m, "id");
    }

    private static Long lng(Map<String, Object> m, String k) {
        String v = str(m, k);
        return v == null ? null : Long.valueOf(v);
    }

    private static Double dbl(Map<String, Object> m, String k) {
        String v = str(m, k);
        return v == null ? null : Double.valueOf(v);
    }

    private static int intOf(Map<String, Object> m, String k, int fallback) {
        String v = str(m, k);
        return v == null ? fallback : Integer.parseInt(v);
    }
}
