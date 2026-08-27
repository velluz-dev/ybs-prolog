package com.ybs.demo.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "route")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_my", nullable = false)
    private String nameMy;

    @Column(name = "base_fare_kyats", nullable = false)
    private Integer baseFareKyats;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNameMy() { return nameMy; }
    public void setNameMy(String nameMy) { this.nameMy = nameMy; }
    public Integer getBaseFareKyats() { return baseFareKyats; }
    public void setBaseFareKyats(Integer baseFareKyats) { this.baseFareKyats = baseFareKyats; }
}
