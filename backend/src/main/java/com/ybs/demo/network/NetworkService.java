package com.ybs.demo.network;

import com.ybs.demo.simulation.BusSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the seeded network once at startup and holds it in memory.
 *
 * <p>The network is 3 routes and 28 stops and never changes while the app runs, so
 * loading it once keeps the 1 Hz simulation tick and the A* search off the database
 * entirely. Both of those run far more often than the data changes.
 */
@Service
public class NetworkService {
    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;

    private final Map<Long, Stop> stopsById = new LinkedHashMap<>();
    private final Map<Long, Route> routesById = new LinkedHashMap<>();
    /** Route code -> route, so hot paths never scan allRoutes() to find one route. */
    private final Map<String, Route> routesByCode = new HashMap<>();
    /** Route id -> its stops in running order. */
    private final Map<Long, List<RouteStop>> itineraries = new HashMap<>();

    public NetworkService(RouteRepository routeRepository,
                          StopRepository stopRepository,
                          RouteStopRepository routeStopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
    }

    /**
     * Loaded on ApplicationReadyEvent, not @PostConstruct. With
     * spring.jpa.defer-datasource-initialization=true, data.sql runs *after* bean
     * construction, so a @PostConstruct loader sees an empty database.
     */
    @Order(1)  // must populate before BusSimulator starts reading itineraries
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        for (Stop s : stopRepository.findAll()) {
            stopsById.put(s.getId(), s);
        }
        for (Route r : routeRepository.findAll()) {
            routesById.put(r.getId(), r);
            routesByCode.put(r.getCode(), r);
            itineraries.put(r.getId(), new ArrayList<>(routeStopRepository.findByRouteIdOrderBySequence(r.getId())));
        }
        log.info("Network cached: {} routes, {} stops", routesById.size(), stopsById.size());
    }

    public Stop stop(Long id) { return stopsById.get(id); }
    public Route route(Long id) { return routesById.get(id); }

    /** Route by its display code (e.g. {@code "43"}), or null if no route has it. */
    public Route routeByCode(String code) { return routesByCode.get(code); }
    public List<Stop> allStops() { return new ArrayList<>(stopsById.values()); }
    public List<Route> allRoutes() { return new ArrayList<>(routesById.values()); }

    /** Stops of a route in running order. */
    public List<RouteStop> itinerary(Long routeId) {
        return itineraries.getOrDefault(routeId, List.of());
    }

    /** Free-flow minutes for the segment starting at {@code index} on this route. */
    public double baseSegmentMinutes(Long routeId, int index) {
        List<RouteStop> it = itinerary(routeId);
        if (index < 0 || index + 1 >= it.size()) {
            return 0;
        }
        return it.get(index + 1).getCumulativeMinutes() - it.get(index).getCumulativeMinutes();
    }

    /** Number of segments (one fewer than the number of stops). */
    public int segmentCount(Long routeId) {
        return Math.max(0, itinerary(routeId).size() - 1);
    }

    /**
     * How many routes call at this stop.
     *
     * <p>Used to decide what counts as an interchange without hand-listing stop ids: a stop
     * served by more than one route is where people change bus, and therefore where boarding
     * takes longest. Derived rather than hardcoded so it stays correct if data.sql changes.
     */
    public int routeCountAt(Long stopId) {
        int count = 0;
        for (List<RouteStop> itinerary : itineraries.values()) {
            for (RouteStop rs : itinerary) {
                if (rs.getStop().getId().equals(stopId)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** True when this stop is the last one on the given route. */
    public boolean isTerminus(Long routeId, Long stopId) {
        List<RouteStop> it = itinerary(routeId);
        return !it.isEmpty() && it.get(it.size() - 1).getStop().getId().equals(stopId);
    }
}
