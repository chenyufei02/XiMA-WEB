package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiDeviceDto {

    @JsonProperty("device_sn")
    private String deviceSn;

    @JsonProperty("device_name")
    private String deviceName;

    // 0: 无人机, 1: 遥控器, 2: 机场
    @JsonProperty("device_type")
    private Integer deviceType;
}