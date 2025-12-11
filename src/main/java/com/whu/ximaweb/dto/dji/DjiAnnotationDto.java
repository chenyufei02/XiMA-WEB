package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 大疆司空2地图标注信息传输对象
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiAnnotationDto {

    private String name;

    @JsonProperty("type")
    private String type; // 例如 "Polygon"

    @JsonProperty("geometry")
    private Geometry geometry;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        // 多边形的经纬度坐标数组: List<List<[lon, lat]>>
        @JsonProperty("coordinates")
        private List<List<List<Double>>> coordinates;

        @JsonProperty("type")
        private String type;
    }
}