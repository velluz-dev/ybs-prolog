package com.ybs.demo.network;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "route_stop")
public class RouteStop {
    @EmbeddedId
    private RouteStopId id;

    @ManyToOne
    @MapsId("routeId")
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne
    @MapsId("stopId")
    @JoinColumn(name = "stop_id")
    private Stop stop;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequence;

    @Column(name = "cumulative_minutes", nullable = false)
    private Integer cumulativeMinutes;

    public RouteStopId getId() { return id; }
    public void setId(RouteStopId id) { this.id = id; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public Stop getStop() { return stop; }
    public void setStop(Stop stop) { this.stop = stop; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public Integer getCumulativeMinutes() { return cumulativeMinutes; }
    public void setCumulativeMinutes(Integer cumulativeMinutes) { this.cumulativeMinutes = cumulativeMinutes; }
}
