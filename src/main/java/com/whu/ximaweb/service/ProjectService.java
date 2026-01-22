package com.whu.ximaweb.service;

import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.model.SysProject;
import java.util.List;

public interface ProjectService {

    // 导入项目
    void importProject(ProjectImportRequest request, Integer userId);

    // 获取用户项目列表
    List<SysProject> getUserProjects(Integer userId);

    // ✅ 新增：删除项目
    void deleteProject(Integer projectId);

    // ✅ 新增：更新项目基本信息
    boolean updateProjectInfo(SysProject project);
}