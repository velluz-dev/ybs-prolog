package com.ybs.demo.ops;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "driver_monthly_score")
public class DriverMonthlyScore {
    @EmbeddedId
    private DriverMonthlyScoreId id;

    @ManyToOne
    @MapsId("driverId")
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "headway_score", nullable = false)
    private Integer headwayScore;

    @Column(name = "harsh_events", nullable = false)
    private Integer harshEvents;

    @Column(name = "upheld_complaints", nullable = false)
    private Integer upheldComplaints;

    @Column(name = "bonus_eligible", nullable = false)
    private Boolean bonusEligible;

    public DriverMonthlyScoreId getId() { return id; }
    public void setId(DriverMonthlyScoreId id) { this.id = id; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public Integer getHeadwayScore() { return headwayScore; }
    public void setHeadwayScore(Integer headwayScore) { this.headwayScore = headwayScore; }
    public Integer getHarshEvents() { return harshEvents; }
    public void setHarshEvents(Integer harshEvents) { this.harshEvents = harshEvents; }
    public Integer getUpheldComplaints() { return upheldComplaints; }
    public void setUpheldComplaints(Integer upheldComplaints) { this.upheldComplaints = upheldComplaints; }
    public Boolean getBonusEligible() { return bonusEligible; }
    public void setBonusEligible(Boolean bonusEligible) { this.bonusEligible = bonusEligible; }
}
