package com.ybs.demo.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stop")
public class Stop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_my", nullable = false)
    private String nameMy;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lon;

    @Column(name = "landmark_photo_url")
    private String landmarkPhotoUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNameMy() { return nameMy; }
    public void setNameMy(String nameMy) { this.nameMy = nameMy; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public String getLandmarkPhotoUrl() { return landmarkPhotoUrl; }
    public void setLandmarkPhotoUrl(String landmarkPhotoUrl) { this.landmarkPhotoUrl = landmarkPhotoUrl; }
}
