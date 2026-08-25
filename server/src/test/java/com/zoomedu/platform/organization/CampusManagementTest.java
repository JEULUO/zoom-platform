package com.zoomedu.platform.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusManagementTest {

    private static final String TEST_USERNAME = "campus-api-test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long assignedCampusId;
    private Long otherCampusId;
    private Long userId;

    @BeforeEach
    void setUpCampusFixtures() {
        jdbcTemplate.update("DELETE FROM sys_operation_audit WHERE module = 'campus'");
        jdbcTemplate.update(
                "DELETE FROM sys_user_campus WHERE user_id IN (SELECT id FROM sys_user WHERE username = ?)",
                TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM org_campus WHERE code LIKE 'TEST_%'");

        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    username, password_hash, display_name, preferred_language, timezone, status
                ) VALUES (?, '{noop}unused', 'Campus API Test', 'zh-CN', 'Asia/Shanghai', 'ACTIVE')
                """, TEST_USERNAME);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                TEST_USERNAME);

        jdbcTemplate.update("""
                INSERT INTO org_campus (code, name, city, sort_order)
                VALUES ('TEST_ASSIGNED', 'Assigned Campus', 'London', 900)
                """);
        jdbcTemplate.update("""
                INSERT INTO org_campus (code, name, city, sort_order)
                VALUES ('TEST_OTHER', 'Other Campus', 'Manchester', 901)
                """);
        assignedCampusId = campusId("TEST_ASSIGNED");
        otherCampusId = campusId("TEST_OTHER");
    }

    @Test
    void managesCampusLifecycleAndRecordsAuditWithAllScope() throws Exception {
        mockMvc.perform(get("/api/v1/campuses")
                        .param("keyword", "test")
                        .with(allScope("campus.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].code").value("TEST_ASSIGNED"));

        MvcResult createdResult = mockMvc.perform(post("/api/v1/campuses")
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "campus-create-request")
                        .content("""
                                {
                                  "code": "test_new",
                                  "name": "New Campus",
                                  "legalName": "Zoom Education New Campus Ltd",
                                  "timezone": "Europe/London",
                                  "countryCode": "gb",
                                  "city": "London",
                                  "contactEmail": "OFFICE@EXAMPLE.COM",
                                  "sortOrder": 902
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST_NEW"))
                .andExpect(jsonPath("$.countryCode").value("GB"))
                .andExpect(jsonPath("$.contactEmail").value("office@example.com"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        JsonNode created = body(createdResult);
        long createdId = created.get("id").asLong();

        mockMvc.perform(post("/api/v1/campuses")
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TEST_NEW",
                                  "name": "Duplicate Campus",
                                  "timezone": "Europe/London",
                                  "countryCode": "GB"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPUS_CODE_EXISTS"));

        mockMvc.perform(put("/api/v1/campuses/{id}", createdId)
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Campus Central",
                                  "legalName": "Zoom Education New Campus Ltd",
                                  "timezone": "Europe/London",
                                  "countryCode": "GB",
                                  "city": "London",
                                  "contactEmail": "office@example.com",
                                  "sortOrder": 902,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Campus Central"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/campuses/{id}/status", createdId)
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(put("/api/v1/campuses/{id}", createdId)
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale Update",
                                  "timezone": "Europe/London",
                                  "countryCode": "GB",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPUS_VERSION_CONFLICT"));

        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_operation_audit
                WHERE module = 'campus' AND resource_id = ? AND result_status = 'SUCCESS'
                """, Integer.class, String.valueOf(createdId));
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    void limitsAssignedScopeToGrantedCampuses() throws Exception {
        mockMvc.perform(get("/api/v1/campuses")
                        .param("keyword", "TEST_")
                        .with(assignedScope("campus.read", "campus.manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(assignedCampusId));

        mockMvc.perform(get("/api/v1/campuses/{id}", otherCampusId)
                        .with(assignedScope("campus.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPUS_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/campuses/{id}", assignedCampusId)
                        .with(assignedScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Assigned Campus Updated",
                                  "timezone": "Europe/London",
                                  "countryCode": "GB",
                                  "city": "London",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Assigned Campus Updated"));

        mockMvc.perform(post("/api/v1/campuses")
                        .with(assignedScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TEST_DENIED",
                                  "name": "Denied Campus",
                                  "timezone": "Europe/London",
                                  "countryCode": "GB"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingPermissionAndInvalidTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/campuses")
                        .with(allScope("campus.manage")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/campuses")
                        .with(allScope("campus.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TEST_TIMEZONE",
                                  "name": "Invalid Timezone Campus",
                                  "timezone": "London/Invalid",
                                  "countryCode": "GB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));

        mockMvc.perform(get("/api/v1/campuses")
                        .param("page", "0")
                        .with(allScope("campus.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/campuses")
                        .param("page", "1000001")
                        .with(allScope("campus.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/campuses")
                        .param("pageSize", "101")
                        .with(allScope("campus.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private JwtRequestPostProcessor allScope(String... permissions) {
        return jwtWithScope("ALL", List.of(), permissions);
    }

    private JwtRequestPostProcessor assignedScope(String... permissions) {
        return jwtWithScope("ASSIGNED_CAMPUSES", List.of(assignedCampusId), permissions);
    }

    private JwtRequestPostProcessor jwtWithScope(
            String dataScope,
            List<Long> campusIds,
            String... permissions) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId.toString())
                        .claim("username", TEST_USERNAME)
                        .claim("dataScope", dataScope)
                        .claim("campusIds", campusIds))
                .authorities(Arrays.stream(permissions)
                        .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                        .toList());
    }

    private Long campusId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM org_campus WHERE code = ?",
                Long.class,
                code);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
