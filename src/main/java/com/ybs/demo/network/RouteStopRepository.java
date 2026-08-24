package com.ybs.demo.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {
    List<RouteStop> findByRouteIdOrderBySequence(Long routeId);
}
