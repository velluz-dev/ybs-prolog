package com.ybs.demo.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {
    /** Stops of a route in running order. Ordered by the sequence inside the composite key. */
    List<RouteStop> findByIdRouteIdOrderByIdSequenceNoAsc(Long routeId);
}
