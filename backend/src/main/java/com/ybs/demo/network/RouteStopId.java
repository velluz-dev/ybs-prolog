package com.ybs.demo.network;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Identifies one position on a route.
 *
 * <p>The key is <b>(route, sequence)</b>, not (route, stop). A route visits positions in
 * order, and the same stop can legitimately appear twice on one route - loop services
 * return to their terminus, and a few routes double back through a junction. In the real
 * YBS data 23 route/stop pairs repeat, which a (route, stop) key would have rejected
 * outright.
 */
@Embeddable
public class RouteStopId implements Serializable {

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

    public RouteStopId() {}

    public RouteStopId(Long routeId, Integer sequenceNo) {
        this.routeId = routeId;
        this.sequenceNo = sequenceNo;
    }

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteStopId that)) return false;
        return Objects.equals(routeId, that.routeId) && Objects.equals(sequenceNo, that.sequenceNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, sequenceNo);
    }
}
