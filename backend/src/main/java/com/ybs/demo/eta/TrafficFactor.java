package com.ybs.demo.eta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A hand-collected traffic prior, NOT a learned model.
 *
 * <p>Each row says: on this segment, during this time-of-day bucket, journeys take
 * {@code factor} times as long as the free-flow base time. The values were authored by
 * hand from local knowledge of which Yangon corridors clog and when. They are stored as
 * data rather than hard-coded so the examiner can see the prior, and so it could later
 * be replaced by counts from real AVL data without touching the code.
 *
 * <p>{@code segmentId} is {@code "<routeCode>:<fromStopId>-<toStopId>"}, or the literal
 * {@code "DEFAULT"} for the fallback row used by any segment without a specific entry.
 */
@Entity
@Table(name = "traffic_factor")
public class TrafficFactor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_id", nullable = false)
    private String segmentId;

    /** One of: morning_peak, midday, evening_peak, night. */
    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false)
    private Double factor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSegmentId() { return segmentId; }
    public void setSegmentId(String segmentId) { this.segmentId = segmentId; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public Double getFactor() { return factor; }
    public void setFactor(Double factor) { this.factor = factor; }
}
