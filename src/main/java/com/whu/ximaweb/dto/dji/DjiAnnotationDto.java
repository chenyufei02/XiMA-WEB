package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 大疆司空2地图标注信息传输对象
 * 主要关注面状标注（Polygon）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiAnnotationDto {

    private String name;

    @JsonProperty("type")
    private String type; // 例如 "Polygon"

    // 标注的几何坐标点
    @JsonProperty("geometry")
    private Geometry geometry;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        @JsonProperty("coordinates")
        private List<List<List<Double>>> coordinates; // 多边形的经纬度坐标数组

        @JsonProperty("type")
        private String type;
    }
}