package com.zoomedu.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationAuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            OperationContext context,
            String module,
            String actionCode,
            String resourceType,
            String resourceId,
            Map<String, Object> detail) {
        jdbcTemplate.update("""
                INSERT INTO sys_operation_audit (
                    user_id, username, module, action_code, resource_type, resource_id,
                    request_id, http_method, request_path, ip_address, result_status, detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?)
                """,
                context.userId(),
                context.username(),
                module,
                actionCode,
                resourceType,
                resourceId,
                context.requestId(),
                context.httpMethod(),
                context.requestPath(),
                context.ipAddress(),
                writeDetail(detail));
    }

    private String writeDetail(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize operation audit detail", exception);
        }
    }
}
