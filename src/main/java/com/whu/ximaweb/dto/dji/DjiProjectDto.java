package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiProjectDto {

    // 修正点1：文档里项目ID字段叫 "uuid"
    @JsonProperty("uuid")
    private String workspaceId;

    @JsonProperty("name")
    private String name;

    // 修正点2：文档里简介字段叫 "introduction"
    @JsonProperty("introduction")
    private String description;
}