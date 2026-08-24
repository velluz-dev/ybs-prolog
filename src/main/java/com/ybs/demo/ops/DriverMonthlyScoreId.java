package com.ybs.demo.ops;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DriverMonthlyScoreId implements Serializable {
    private Long driverId;
    private String monthKey;

    public DriverMonthlyScoreId() {}

    public DriverMonthlyScoreId(Long driverId, String monthKey) {
        this.driverId = driverId;
        this.monthKey = monthKey;
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getMonthKey() { return monthKey; }
    public void setMonthKey(String monthKey) { this.monthKey = monthKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverMonthlyScoreId)) return false;
        DriverMonthlyScoreId that = (DriverMonthlyScoreId) o;
        return Objects.equals(driverId, that.driverId) && Objects.equals(monthKey, that.monthKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driverId, monthKey);
    }
}
