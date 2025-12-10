package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 大疆司空2项目信息传输对象
 * 用于映射API返回的 JSON 数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略JSON中我们不需要的字段
public class DjiProjectDto {

    @JsonProperty("workspace_id") // 对应大疆JSON中的字段名
    private String workspaceId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;
}