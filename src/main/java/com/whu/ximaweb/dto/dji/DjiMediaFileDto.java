package com.whu.ximaweb.dto.dji;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 大疆司空2媒体文件信息传输对象
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DjiMediaFileDto {

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("file_path")
    private String filePath; // 也就是文件夹路径

    @JsonProperty("url") // 下载链接
    private String downloadUrl;

    // 扩展元数据，如下载链接过期时间等，按需添加
}