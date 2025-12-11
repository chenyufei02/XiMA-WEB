package com.whu.ximaweb.service;

import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.model.SysProject;
import java.util.List;

public interface ProjectService {
    /**
     * 导入并保存新项目
     */
    void importProject(ProjectImportRequest request, Integer userId);

    /**
     * 获取指定用户的所有项目
     */
    List<SysProject> getUserProjects(Integer userId);
}