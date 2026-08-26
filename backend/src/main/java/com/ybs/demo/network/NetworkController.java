package com.ybs.demo.network;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network, split into two calls on purpose.
 *
 * <p>{@code /api/network} is the index: every stop, plus route <i>metadata</i> only. On the
 * real 118-route network, inlining each route's stop list and road geometry would make that
 * one response several megabytes, which is a slow first paint for data the user has not
 * asked to see. {@code /api/routes/{code}} then returns one route's stops and road shape,
 * fetched only when that route is switched on.
 */
@RestController
@RequestMapping("/api")
public class NetworkController {

    /**
     * Distinct, high-contrast colours cycled across routes.
     *
     * <p>Twelve genuinely different hues, all dark enough to read against a pale street
     * map. An earlier list repeated hues - two blues, two purples, two teals - which meant
     * two routes drawn side by side could look like the same line. With 118 routes no
     * palette makes every pair distinguishable, so the map draws only a few at a time and
     * the default selection skips colours already taken.
     */
    private static final String[] PALETTE = {
            "#e6194b",  // red
            "#3cb44b",  // green
            "#4363d8",  // blue
            "#f58231",  // orange
            "#911eb4",  // purple
            "#469990",  // teal
            "#f032e6",  // magenta
            "#9a6324",  // brown
            "#800000",  // maroon
            "#808000",  // olive
            "#000075",  // navy
            "#e6ab02"   // gold
    };

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteShapeService shapes;
    private final NetworkService network;

    public NetworkController(RouteRepository routeRepository,
                             StopRepository stopRepository,
                             RouteStopRepository routeStopRepository,
                             RouteShapeService shapes,
                             NetworkService network) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
        this.shapes = shapes;
        this.network = network;
    }

    static String colourFor(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    /** Index: all stops, plus route metadata without stop lists or geometry. */
    @GetMapping("/network")
    public Map<String, Object> network() {
        List<Map<String, Object>> stops = new ArrayList<>();
        for (Stop s : stopRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("nameMy", s.getNameMy());
            m.put("nameEn", s.getNameEn());
            m.put("lat", s.getLat());
            m.put("lon", s.getLon());
            m.put("landmarkPhotoUrl", s.getLandmarkPhotoUrl());
            stops.add(m);
        }

        List<Route> all = routeRepository.findAll();
        List<Map<String, Object>> routes = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Route r = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getCode());
            m.put("nameMy", r.getNameMy());
            m.put("baseFareKyats", r.getBaseFareKyats());
            m.put("colour", colourFor(i));
            // Lets the map default to the major cross-town routes rather than
            // whichever six happen to sort first.
            m.put("stopCount", network.itinerary(r.getId()).size());
            routes.add(m);
        }

        return Map.of("routes", routes, "stops", stops);
    }

    /** One route in full: its stops in running order, and its road-following shape. */
    @GetMapping("/routes/{code}")
    public ResponseEntity<Map<String, Object>> route(@PathVariable String code) {
        List<Route> all = routeRepository.findAll();
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getCode().equals(code)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return ResponseEntity.notFound().build();
        }
        Route route = all.get(index);

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (RouteStop rs : routeStopRepository.findByIdRouteIdOrderByIdSequenceNoAsc(route.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stopId", rs.getStop().getId());
            m.put("nameMy", rs.getStop().getNameMy());
            m.put("nameEn", rs.getStop().getNameEn());
            m.put("lat", rs.getStop().getLat());
            m.put("lon", rs.getStop().getLon());
            m.put("sequence", rs.getSequence());
            m.put("cumulativeMinutes", rs.getCumulativeMinutes());
            ordered.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", route.getId());
        out.put("code", route.getCode());
        out.put("nameMy", route.getNameMy());
        out.put("baseFareKyats", route.getBaseFareKyats());
        out.put("colour", colourFor(index));
        out.put("stops", ordered);
        // Empty when geometry could not be fetched; the frontend then joins the stops.
        out.put("shape", shapes.shapeOf(route.getCode()));
        return ResponseEntity.ok(out);
    }
}
