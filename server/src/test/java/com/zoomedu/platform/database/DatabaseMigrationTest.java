package com.zoomedu.platform.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoomedu.platform.organization.CampusMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CampusMapper campusMapper;

    @Test
    void createsFoundationSchemaAndSeedsReferenceData() {
        List<String> roleCodes = jdbcTemplate.queryForList(
                "SELECT code FROM sys_role ORDER BY sort_order", String.class);

        assertThat(roleCodes).containsExactly(
                "SUPER_ADMIN",
                "ORG_ADMIN",
                "CAMPUS_ADMIN",
                "FINANCE",
                "ACADEMIC",
                "TEACHER",
                "PARENT",
                "STUDENT");
        assertThat(count("sys_permission")).isEqualTo(15);
        assertThat(count("org_campus")).isEqualTo(4);
        assertThat(count("sys_role_permission")).isGreaterThan(30);
        assertThat(campusMapper.findActiveCampuses())
                .extracting(campus -> campus.code())
                .containsExactly("KINGSTON", "LEICESTER_SQUARE", "PUTNEY", "RICHMOND");
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
