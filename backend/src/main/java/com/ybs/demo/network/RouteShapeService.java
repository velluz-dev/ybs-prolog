package com.ybs.demo.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Road-following geometry for each route.
 *
 * <p>Drawing a route as straight lines between stops cuts across blocks and buildings. The
 * real shape follows the road, which needs a routing engine - so the geometry was fetched
 * once from OSRM at build time and committed as {@code route_shapes.json}. The demo itself
 * makes no network call: the file ships inside the jar.
 *
 * <p>Routes whose geometry could not be fetched fall back to their straight-line stop
 * sequence in that same file, so every route always has something to draw.
 */
@Service
public class RouteShapeService {
    private static final Logger log = LoggerFactory.getLogger(RouteShapeService.class);

    /** Route code -> ordered [lat, lon] pairs along the road. */
    private Map<String, List<List<Double>>> shapes = Map.of();

    public RouteShapeService(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("route_shapes.json").getInputStream()) {
            shapes = mapper.readValue(in, mapper.getTypeFactory()
                    .constructMapType(java.util.HashMap.class, String.class, List.class));
            int points = shapes.values().stream().mapToInt(List::size).sum();
            log.info("Route shapes loaded: {} routes, {} geometry points", shapes.size(), points);
        } catch (Exception e) {
            // Not fatal: the frontend falls back to joining stops with straight lines.
            log.warn("route_shapes.json not available ({}); routes will draw as straight lines",
                    e.getMessage());
        }
    }

    public List<List<Double>> shapeOf(String routeCode) {
        return shapes.getOrDefault(routeCode, List.of());
    }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }
}
