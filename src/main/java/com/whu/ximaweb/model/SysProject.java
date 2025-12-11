package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_project")
public class SysProject {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String projectName;
    private String photoFolderKeyword;
    // 大疆配置
    private String djiProjectUuid;
    private String djiOrgKey;
    // 华为云配置
    private String obsBucketName;
    private String obsAk;
    private String obsSk;
    private String obsEndpoint;

    private Integer createdBy;
    private LocalDateTime createdAt;

    /**
     * 电子围栏坐标集合 (JSON字符串格式)
     * 例如: [{"lat":30.5, "lng":114.3}, {"lat":30.6, "lng":114.4}]
     */
    private String boundaryCoords;
}