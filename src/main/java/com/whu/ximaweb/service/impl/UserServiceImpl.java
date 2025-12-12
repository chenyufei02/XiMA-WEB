package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.LoginRequest;
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.UserService;
import com.whu.ximaweb.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public String login(LoginRequest request) {
        // 1. 根据用户名查询用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", request.getUsername());
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        // 2. 校验用户是否存在
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 3. 校验密码
        // 注意：数据库中密码为明文存储，直接比对即可
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 4. 登录成功，生成并返回 Token
        // 将用户ID和用户名放入 Token 中，方便后续使用
        return jwtUtils.generateToken(user.getId(), user.getUsername());
    }
}