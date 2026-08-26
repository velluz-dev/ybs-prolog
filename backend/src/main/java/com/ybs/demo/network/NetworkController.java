package com.ybs.demo.network;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole network in one call: every stop, and every route as an ordered stop list.
 * The frontend draws polylines straight from this, so the map needs exactly one request.
 */
@RestController
@RequestMapping("/api")
public class NetworkController {

    /** Fixed per-route colours so the map legend matches across reloads.
     *  The first three are the hand-built demo corridors; the rest are the
     *  imported real YBS routes (see data.sql extension section). */
    private static final Map<String, String> ROUTE_COLOURS = Map.of(
            "36", "#e6194b",
            "43", "#3cb44b",
            "61", "#4363d8",
            "57", "#f58231",
            "56", "#911eb4",
            "58", "#008080",
            "APS-Pyay", "#f032e6",
            "APS-Kabaraye", "#800000");

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;

    public NetworkController(RouteRepository routeRepository,
                             StopRepository stopRepository,
                             RouteStopRepository routeStopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
    }

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

        List<Map<String, Object>> routes = new ArrayList<>();
        for (Route r : routeRepository.findAll()) {
            List<Map<String, Object>> ordered = new ArrayList<>();
            for (RouteStop rs : routeStopRepository.findByRouteIdOrderBySequence(r.getId())) {
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getCode());
            m.put("nameMy", r.getNameMy());
            m.put("baseFareKyats", r.getBaseFareKyats());
            m.put("colour", ROUTE_COLOURS.getOrDefault(r.getCode(), "#808080"));
            m.put("stops", ordered);
            routes.add(m);
        }

        return Map.of("routes", routes, "stops", stops);
    }
}
