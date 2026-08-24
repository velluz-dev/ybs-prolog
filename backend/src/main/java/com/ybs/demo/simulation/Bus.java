package com.ybs.demo.simulation;

import com.ybs.demo.network.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "bus")
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "current_lat", nullable = false)
    private Double currentLat;

    @Column(name = "current_lon", nullable = false)
    private Double currentLon;

    @Column(name = "current_segment_index", nullable = false)
    private Integer currentSegmentIndex;

    @Column(name = "progress_along_segment", nullable = false)
    private Double progressAlongSegment;

    @Column(name = "crowding_level", nullable = false)
    private Integer crowdingLevel;

    /**
     * Seconds left standing at the current stop; 0 means the bus is driving.
     *
     * <p>Transient on purpose. This is live simulation state, not something the seed data
     * describes, so it needs no column and no data.sql change. That is consistent with how
     * the rest of this entity is already used - positions are mutated in memory every tick
     * and never written back to the database.
     */
    @Transient
    private double dwellSecondsRemaining = 0;

    public boolean isDwelling() { return dwellSecondsRemaining > 0; }

    /** "DRIVING" or "DWELLING" - what the frontend shows as a state badge. */
    public String getState() { return isDwelling() ? "DWELLING" : "DRIVING"; }

    public double getDwellSecondsRemaining() { return dwellSecondsRemaining; }
    public void setDwellSecondsRemaining(double seconds) { this.dwellSecondsRemaining = Math.max(0, seconds); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public Double getCurrentLat() { return currentLat; }
    public void setCurrentLat(Double currentLat) { this.currentLat = currentLat; }
    public Double getCurrentLon() { return currentLon; }
    public void setCurrentLon(Double currentLon) { this.currentLon = currentLon; }
    public Integer getCurrentSegmentIndex() { return currentSegmentIndex; }
    public void setCurrentSegmentIndex(Integer currentSegmentIndex) { this.currentSegmentIndex = currentSegmentIndex; }
    public Double getProgressAlongSegment() { return progressAlongSegment; }
    public void setProgressAlongSegment(Double progressAlongSegment) { this.progressAlongSegment = progressAlongSegment; }
    public Integer getCrowdingLevel() { return crowdingLevel; }
    public void setCrowdingLevel(Integer crowdingLevel) { this.crowdingLevel = crowdingLevel; }
}
