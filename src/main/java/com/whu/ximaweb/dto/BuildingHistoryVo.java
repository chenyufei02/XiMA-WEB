package com.whu.ximaweb.dto;

import lombok.Data;

@Data
public class BuildingHistoryVo {
    /** 日期 (yyyy-MM-dd) */
    private String date;

    /** 当天楼层 */
    private Integer floor;

    /** 当天高度 */
    private Double height;

    /** 当天代表性照片 URL (用于播放) */
    private String photoUrl;

    /** 状态标签 (如: "正常", "滞后") - 可选，用于前端展示 */
    private String status;
}