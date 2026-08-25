package com.zoomedu.platform.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDirectoryTest {

    private static final String USERNAME_PREFIX = "dir_";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private Long actorUserId;
    private Long sharedUserId;
    private Long otherUserId;
    private Long campusAId;
    private Long campusBId;

    @BeforeEach
    void setUpDirectoryFixtures() {
        jdbcTemplate.update(
                "DELETE FROM sys_user_campus WHERE user_id IN "
                        + "(SELECT id FROM sys_user WHERE username LIKE ?)",
                USERNAME_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id IN "
                        + "(SELECT id FROM sys_user WHERE username LIKE ?)",
                USERNAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", USERNAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM org_campus WHERE code LIKE 'DIR_%'");

        jdbcTemplate.update("""
                INSERT INTO org_campus (code, name, city, sort_order)
                VALUES ('DIR_CAMPUS_A', 'Directory Campus A', 'London', 950)
                """);
        jdbcTemplate.update("""
                INSERT INTO org_campus (code, name, city, sort_order)
                VALUES ('DIR_CAMPUS_B', 'Directory Campus B', 'Manchester', 951)
                """);
        campusAId = campusId("DIR_CAMPUS_A");
        campusBId = campusId("DIR_CAMPUS_B");

        insertUser("dir_actor", "Directory Actor", "ACTIVE", "actor@example.com");
        insertUser("dir_shared", "Directory Shared", "ACTIVE", "shared@example.com");
        insertUser("dir_other", "Directory Other", "LOCKED", "other@example.com");
        insertUser("dir_unassigned", "Directory Unassigned", "PENDING", null);
        actorUserId = userId("dir_actor");
        sharedUserId = userId("dir_shared");
        otherUserId = userId("dir_other");

        assignCampus(actorUserId, campusAId, true);
        assignCampus(sharedUserId, campusAId, true);
        assignCampus(sharedUserId, campusBId, false);
        assignCampus(otherUserId, campusBId, true);
        assignRole(sharedUserId, "CAMPUS_ADMIN");
        assignRole(otherUserId, "FINANCE");
    }

    @Test
    void listsAndReadsUsersWithAllScope() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "dir_")
                        .with(allScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].username").value("dir_actor"));

        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "dir_")
                        .param("campusId", campusAId.toString())
                        .with(allScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/users/{id}", sharedUserId)
                        .with(allScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dir_shared"))
                .andExpect(jsonPath("$.roles[0].code").value("CAMPUS_ADMIN"))
                .andExpect(jsonPath("$.campuses.length()").value(2));

        mockMvc.perform(get("/api/v1/users/options")
                        .with(allScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.length()").value(4))
                .andExpect(jsonPath("$.campuses[?(@.code == 'DIR_CAMPUS_A')]").exists());
    }

    @Test
    void limitsAssignedScopeAndRedactsOtherCampusAssignments() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "dir_")
                        .with(assignedScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].username")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("dir_actor", "dir_shared")));

        mockMvc.perform(get("/api/v1/users/{id}", sharedUserId)
                        .with(assignedScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campuses.length()").value(1))
                .andExpect(jsonPath("$.campuses[0].code").value("DIR_CAMPUS_A"));

        mockMvc.perform(get("/api/v1/users/{id}", otherUserId)
                        .with(assignedScope("user.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "dir_")
                        .param("campusId", campusBId.toString())
                        .with(assignedScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/users/options")
                        .with(assignedScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campuses.length()").value(1))
                .andExpect(jsonPath("$.campuses[0].code").value("DIR_CAMPUS_A"));
    }

    @Test
    void supportsSelfScopeAndRejectsMissingPermissionOrInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("keyword", "dir_")
                        .with(selfScope("user.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("dir_actor"));

        mockMvc.perform(get("/api/v1/users")
                        .with(allScope("campus.read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .with(allScope("user.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/users")
                        .param("pageSize", "101")
                        .with(allScope("user.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void insertUser(String username, String displayName, String status, String email) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    username, password_hash, display_name, email,
                    preferred_language, timezone, status
                ) VALUES (?, '{noop}unused', ?, ?, 'zh-CN', 'Asia/Shanghai', ?)
                """, username, displayName, email, status);
    }

    private void assignCampus(Long userId, Long campusId, boolean primaryCampus) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_campus (user_id, campus_id, primary_campus)
                VALUES (?, ?, ?)
                """, userId, campusId, primaryCampus);
    }

    private void assignRole(Long userId, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE code = ?
                """, userId, roleCode);
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                username);
    }

    private Long campusId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM org_campus WHERE code = ?",
                Long.class,
                code);
    }

    private JwtRequestPostProcessor allScope(String... permissions) {
        return jwtWithScope("ALL", List.of(), permissions);
    }

    private JwtRequestPostProcessor assignedScope(String... permissions) {
        return jwtWithScope("ASSIGNED_CAMPUSES", List.of(campusAId), permissions);
    }

    private JwtRequestPostProcessor selfScope(String... permissions) {
        return jwtWithScope("SELF", List.of(campusAId), permissions);
    }

    private JwtRequestPostProcessor jwtWithScope(
            String dataScope,
            List<Long> campusIds,
            String... permissions) {
        return jwt()
                .jwt(builder -> builder
                        .subject(actorUserId.toString())
                        .claim("username", "dir_actor")
                        .claim("dataScope", dataScope)
                        .claim("campusIds", campusIds))
                .authorities(Arrays.stream(permissions)
                        .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                        .toList());
    }
}
