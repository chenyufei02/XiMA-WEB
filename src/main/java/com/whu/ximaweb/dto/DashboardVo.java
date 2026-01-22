package com.whu.ximaweb.dto;

import lombok.Data;
import java.util.List;

/**
 * 项目看板视图对象 (完整版)
 * 用于封装 dashboard.html 所需的所有数据，包括概览指标和图表数据
 */
@Data
public class DashboardVo {
    // --- 顶部概览数据 ---
    private String projectName;
    private long safeRunDays;      // 安全运行天数
    private String lastUpdateDate; // 最后一次更新时间
    private int totalBuildings;    // 总楼栋数
    private int delayedCount;      // 滞后楼栋数
    private int normalCount;       // 正常楼栋数
    private int aheadCount;        // 超前楼栋数
    private int waitingCount;      // 待数据/过期楼栋数

    // --- 下方楼栋列表数据 ---
    private List<BuildingProgressVo> buildings;

    @Data
    public static class BuildingProgressVo {
        private Integer buildingId;
        private String buildingName;   // 显示名称 (如: 1号楼)
        private String planName;       // 绑定的Navisworks名

        // 当前状态
        private Integer currentFloor;  // 当前实际楼层
        private Double currentHeight;  // 当前物理高度 (米)

        private String statusTag;      // 状态标签 (如: "严重滞后", "暂无数据")
        private String statusColor;    // 标签颜色 (danger, success, primary, info)
        private String lastMeasureDate;// 最新测量日期
        private boolean isOutdated;    // 是否数据已过期 (>7天)

        // 图表数据 (用于 ECharts)
        private List<String> dates;           // X轴: 日期
        private List<Integer> actualFloors;   // Y轴(图1): 实际楼层
        private List<Integer> planFloors;     // Y轴(图1): 计划楼层
        private List<Double> actualHeights;   // Y轴(图2): 实际物理高度(米)
        private List<Integer> deviations;     // Y轴(图3): 偏差层数
    }
}