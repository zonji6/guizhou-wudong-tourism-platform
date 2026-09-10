package com.guizhou.wudong.v3;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "wudong.admin-init", havingValue = "true")
public class V3DemoAdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String passwordFile;

    public V3DemoAdminInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${wudong.admin-username:wudong_admin}") String username,
            @Value("${wudong.admin-password-file:}") String passwordFile) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.passwordFile = passwordFile;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (username.isBlank() || passwordFile.isBlank()) {
            throw new IllegalStateException("本机管理员初始化配置不完整");
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_account WHERE username = ?",
                Integer.class,
                username);
        if (count != null && count > 0) {
            return;
        }
        String password = Files.readString(Path.of(passwordFile), StandardCharsets.US_ASCII).trim();
        if (password.length() < 8) {
            throw new IllegalStateException("本机管理员密码文件无效");
        }
        jdbc.update(
                "INSERT INTO platform_account(id, username, password_hash, nickname, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                UUID.randomUUID().toString(),
                username,
                passwordEncoder.encode(password),
                "乌东运营");
    }
}
