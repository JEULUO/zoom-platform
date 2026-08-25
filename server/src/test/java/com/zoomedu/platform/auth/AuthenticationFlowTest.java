package com.zoomedu.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthenticationFlowTest {

    private static final String USERNAME = "auth-test-admin";
    private static final String PASSWORD = "AuthTest@2026!";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createActiveAdministrator() {
        jdbcTemplate.update(
                "DELETE FROM sys_login_audit WHERE username_attempted = ?", USERNAME);
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = ?)",
                USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);

        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    username, password_hash, display_name, preferred_language, timezone,
                    status, password_changed_at
                ) VALUES (?, ?, 'Authentication Test Admin', 'zh-CN', 'Asia/Shanghai',
                          'ACTIVE', CURRENT_TIMESTAMP)
                """, USERNAME, passwordEncoder.encode(PASSWORD));
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT account.id, role.id
                FROM sys_user account
                JOIN sys_role role ON role.code = 'SUPER_ADMIN'
                WHERE account.username = ?
                """, USERNAME);
    }

    @Test
    void rotatesRefreshTokenAndRevokesSessionOnLogout() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "zoom-platform-integration-test")
                        .header("X-Request-Id", "login-request")
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE, true))
                .andExpect(cookie().sameSite(AuthController.REFRESH_COOKIE, "Strict"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.username").value(USERNAME))
                .andExpect(jsonPath("$.user.roles", hasItem("SUPER_ADMIN")))
                .andExpect(jsonPath("$.user.permissions", hasItem("platform.manage")))
                .andExpect(jsonPath("$.user.dataScope").value("ALL"))
                .andReturn();

        String accessToken = body(login).get("accessToken").asText();
        Cookie firstRefreshCookie = login.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(firstRefreshCookie).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Authentication Test Admin"));

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE))
                .andReturn();
        String refreshedAccessToken = body(refresh).get("accessToken").asText();
        Cookie rotatedRefreshCookie = refresh.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(firstRefreshCookie.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + refreshedAccessToken)
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE, 0));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_REVOKED"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        Integer successfulEvents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_login_audit
                WHERE username_attempted = ? AND success = TRUE
                  AND event_type IN ('LOGIN', 'TOKEN_REFRESH', 'LOGOUT')
                """, Integer.class, USERNAME);
        assertThat(successfulEvents).isEqualTo(3);
    }

    @Test
    void locksAccountAfterFiveFailedPasswords() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("WrongPassword@2026!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        Integer failedAttempts = jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM sys_user WHERE username = ?",
                Integer.class,
                USERNAME);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE username = ?",
                String.class,
                USERNAME);
        Integer failedAudits = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_login_audit
                WHERE username_attempted = ? AND success = FALSE AND event_type = 'LOGIN'
                """, Integer.class, USERNAME);

        assertThat(failedAttempts).isEqualTo(5);
        assertThat(status).isEqualTo("LOCKED");
        assertThat(failedAudits).isEqualTo(6);
    }

    private String loginBody(String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequest(USERNAME, password));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
