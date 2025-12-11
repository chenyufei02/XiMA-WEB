package com.whu.ximaweb.dto;

import lombok.Data;

@Data
public class Coordinate {
    private Double lat; // 纬度
    private Double lng; // 经度

    // 方便构造
    public Coordinate() {}
    public Coordinate(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}