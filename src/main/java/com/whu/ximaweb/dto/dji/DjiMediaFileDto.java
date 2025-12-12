package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 大疆媒体文件 DTO
 * 用于接收 /flight-task/{id}/media 接口返回的照片信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiMediaFileDto {

    // 大疆API返回的字段通常是 "name" 或 "file_name"
    // 我们用 @JsonAlias 兼容多种情况，确保万无一失
    @JsonProperty("name")
    private String fileName;

    // 大疆API返回的下载链接通常是 "original_url"
    @JsonProperty("original_url")
    private String downloadUrl;

    // 文件路径 (这是我们在 Service 里人工合成的，API不返回这个，所以不需要 @JsonProperty)
    private String filePath;
}