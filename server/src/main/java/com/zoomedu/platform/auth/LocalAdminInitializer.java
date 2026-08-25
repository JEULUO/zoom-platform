package com.zoomedu.platform.auth;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
class LocalAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    LocalAdminInitializer(
            BootstrapAdminProperties properties,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("Local bootstrap administrator password must not be blank");
        }

        String username = properties.username().trim().toLowerCase(Locale.ROOT);
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        Long userId = userIds.isEmpty() ? createAdministrator(username) : userIds.get(0);

        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, assigned_by)
                SELECT ?, role.id, ?
                FROM sys_role role
                WHERE role.code = 'SUPER_ADMIN'
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_user_role existing
                    WHERE existing.user_id = ? AND existing.role_id = role.id
                  )
                """, userId, userId, userId);
    }

    private Long createAdministrator(String username) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sys_user (
                        username, password_hash, display_name, preferred_language, timezone,
                        status, password_changed_at
                    ) VALUES (?, ?, ?, 'zh-CN', 'Europe/London', 'ACTIVE', CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordEncoder.encode(properties.password()));
            statement.setString(3, properties.displayName());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return the local administrator id");
        }
        log.info("Created local bootstrap administrator account: {}", username);
        return key.longValue();
    }
}
