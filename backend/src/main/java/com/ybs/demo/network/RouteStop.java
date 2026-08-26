package com.ybs.demo.network;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * One stop at one position on one route.
 *
 * <p>Keyed by (route, sequence) - see {@link RouteStopId} for why the stop is not part of
 * the key.
 */
@Entity
@Table(name = "route_stop")
public class RouteStop {

    @EmbeddedId
    private RouteStopId id;

    @ManyToOne
    @MapsId("routeId")
    @JoinColumn(name = "route_id")
    private Route route;

    /** Not part of the key: the same stop may appear more than once on a route. */
    @ManyToOne
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(name = "cumulative_minutes", nullable = false)
    private Integer cumulativeMinutes;

    public RouteStopId getId() { return id; }
    public void setId(RouteStopId id) { this.id = id; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public Stop getStop() { return stop; }
    public void setStop(Stop stop) { this.stop = stop; }

    /** Position on the route, taken from the key. */
    public Integer getSequence() { return id == null ? null : id.getSequenceNo(); }

    public Integer getCumulativeMinutes() { return cumulativeMinutes; }
    public void setCumulativeMinutes(Integer cumulativeMinutes) { this.cumulativeMinutes = cumulativeMinutes; }
}
