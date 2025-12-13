package com.whu.ximaweb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件服务类
 * 负责发送验证码邮件，并维护验证码的有效期
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // 内存缓存：存储 <邮箱, 验证码信息>
    private final Map<String, CodeItem> codeCache = new ConcurrentHashMap<>();

    /**
     * 发送验证码邮件
     * @param toEmail 收件人邮箱
     */
    public void sendVerifyCode(String toEmail) {
        // 1. 生成6位随机验证码
        String code = String.valueOf(new Random().nextInt(899999) + 100000);

        // 2. 存入缓存 (有效期 5 分钟)
        codeCache.put(toEmail, new CodeItem(code, System.currentTimeMillis() + 5 * 60 * 1000));

        // 3. 发送邮件
        SimpleMailMessage message = new SimpleMailMessage();

        // 设置自定义发件人名称：格式为 "显示名称 <邮箱地址>"
        // 注意：这里的邮箱地址必须和 application.properties 里的 username 一致，否则会被服务器拒绝
        String nickName = "进度自动管控系统";
        message.setFrom(nickName + " <" + fromEmail + ">");

        message.setTo(toEmail);
        message.setSubject("【进度自动管控系统】注册验证码");
        message.setText("您好！\n\n您的注册验证码是：" + code + "。\n\n该验证码在5分钟内有效。为了保障您的账号安全，请勿将验证码泄露给他人。");

        mailSender.send(message);
        System.out.println("✅ 邮件已发送至: " + toEmail + "，验证码: " + code);
    }

    /**
     * 校验验证码
     * @param email 邮箱
     * @param inputCode 用户输入的验证码
     * @return 是否验证通过
     */
    public boolean verifyCode(String email, String inputCode) {
        CodeItem item = codeCache.get(email);
        if (item == null) return false; // 没发过或已过期清除

        // 检查是否过期
        if (System.currentTimeMillis() > item.expireTime) {
            codeCache.remove(email);
            return false;
        }

        // 检查验证码匹配
        if (item.code.equals(inputCode)) {
            codeCache.remove(email); // 验证成功后立即移除，防止重复使用
            return true;
        }
        return false;
    }

    // 内部类：验证码条目
    private static class CodeItem {
        String code;
        long expireTime;

        public CodeItem(String code, long expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }
    }
}