package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.LoginRequest;
import com.whu.ximaweb.dto.RegisterDto;
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.EmailService;
import com.whu.ximaweb.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理登录、注册、验证码发送
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SysUserMapper sysUserMapper; // 直接注入 Mapper 处理注册入库

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String password,
                                                  @RequestBody(required = false) LoginRequest body) {
        // 兼容两种参数传递方式：Query Param 或 JSON Body
        String finalUser = username != null ? username : (body != null ? body.getUsername() : null);
        String finalPass = password != null ? password : (body != null ? body.getPassword() : null);

        // 构造临时请求对象传给 Service
        LoginRequest request = new LoginRequest();
        request.setUsername(finalUser);
        request.setPassword(finalPass);

        try {
            String token = userService.login(request);
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            data.put("username", finalUser); // 返回用户名方便前端存储
            return ApiResponse.success("登录成功", data);
        } catch (Exception e) {
            return ApiResponse.error(401, "登录失败: " + e.getMessage());
        }
    }

    /**
     * 发送验证码接口
     * POST /api/auth/send-code?email=xxx@xx.com
     */
    @PostMapping("/send-code")
    public ApiResponse<String> sendCode(@RequestParam String email) {
        if (email == null || !email.contains("@")) {
            return ApiResponse.error("邮箱格式不正确");
        }
        try {
            // 检查邮箱是否已被注册
            QueryWrapper<SysUser> query = new QueryWrapper<>();
            query.eq("email", email);
            if (sysUserMapper.selectCount(query) > 0) {
                return ApiResponse.error("该邮箱已被注册，请直接登录");
            }

            emailService.sendVerifyCode(email);
            return ApiResponse.success("验证码已发送，请查收邮件");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("邮件发送失败，请检查邮箱地址或联系管理员");
        }
    }

    /**
     * 注册接口
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterDto dto) {
        // 1. 基础校验
        if (dto.getUsername() == null || dto.getPassword() == null) {
            return ApiResponse.error("用户名或密码不能为空");
        }

        // 2. 校验验证码
        boolean isCodeValid = emailService.verifyCode(dto.getEmail(), dto.getCode());
        if (!isCodeValid) {
            return ApiResponse.error("验证码错误或已失效");
        }

        // 3. 检查用户名唯一性
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.eq("username", dto.getUsername());
        if (sysUserMapper.selectCount(query) > 0) {
            return ApiResponse.error("用户名 [" + dto.getUsername() + "] 已被占用");
        }

        // 4. 入库保存
        try {
            SysUser newUser = new SysUser();
            newUser.setUsername(dto.getUsername());
            newUser.setPassword(dto.getPassword()); // 存明文密码(毕设保持一致)
            newUser.setEmail(dto.getEmail());
            newUser.setPhone(dto.getPhone());
            newUser.setCreatedAt(LocalDateTime.now());
            // 🔥 [核心修改] 优先使用用户输入的真实姓名，如果没填，则默认使用用户名
            if (dto.getRealName() != null && !dto.getRealName().trim().isEmpty()) {
                newUser.setRealName(dto.getRealName());
            } else {
                newUser.setRealName(dto.getUsername()); // 默认用账号名，比"新用户"更好识别
            }

            sysUserMapper.insert(newUser);
            return ApiResponse.success("注册成功！请前往登录页面");
        } catch (Exception e) {
            return ApiResponse.error("注册失败: " + e.getMessage());
        }
    }
}