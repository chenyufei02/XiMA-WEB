package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 大疆飞行任务数据传输对象
 * 用于接收 /flight-task/list 接口返回的任务信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiTaskDto {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private String status; // 例如: "success", "ready"

    @JsonProperty("begin_at")
    private String beginAt; // 任务开始时间
}