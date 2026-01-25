package com.whu.ximaweb.dto;

import lombok.Data;
import java.util.List;

/**
 * 看板视图对象
 */
@Data
public class DashboardVo {
    // === 核心修复：新增字段 projectId ===
    private Integer projectId;

    private String projectName;
    private Long safeRunDays;
    private Integer totalBuildings;

    // 状态统计
    private Integer delayedCount;
    private Integer normalCount;
    private Integer aheadCount;
    private Integer waitingCount;

    private String lastUpdateDate;

    private List<BuildingProgressVo> buildings;

    @Data
    public static class BuildingProgressVo {
        private Integer buildingId;
        private String buildingName;
        private String planName;

        private Integer currentFloor;
        private Double currentHeight;
        private String statusTag;
        private String statusColor; // success, warning, danger, info, primary
        private String lastMeasureDate;
        private boolean isOutdated; // 是否数据陈旧

        // 图表数据
        private List<String> dates;
        private List<Integer> planFloors;
        private List<Integer> actualFloors;
        private List<Double> actualHeights;
        private List<Integer> deviations;

        // === 核心修复：新增字段 photoCounts ===
        private List<Integer> photoCounts;
    }
}