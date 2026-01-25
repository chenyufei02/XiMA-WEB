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
     * 是否开启每日AI进度监察报告 (1=开启, 0=关闭)
     */
    private Integer enableAiReport;

    // 注意：boundaryCoords 字段已被移除，现已迁移至 SysBuilding 表中
}