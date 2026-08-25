package com.zoomedu.platform.auth;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface AuthMapper {

    @Select("""
            SELECT id, username, password_hash, display_name, preferred_language,
                   timezone, status, failed_login_attempts, locked_until
            FROM sys_user
            WHERE username = #{username}
            """)
    UserAccount findByUsername(String username);

    @Select("""
            SELECT id, username, password_hash, display_name, preferred_language,
                   timezone, status, failed_login_attempts, locked_until
            FROM sys_user
            WHERE id = #{id}
            """)
    UserAccount findById(Long id);

    @Select("""
            SELECT role.code, role.data_scope
            FROM sys_role role
            JOIN sys_user_role user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = #{userId}
              AND role.status = 'ACTIVE'
            ORDER BY role.sort_order, role.code
            """)
    List<RoleGrant> findRoleGrants(Long userId);

    @Select("""
            SELECT DISTINCT permission.code
            FROM sys_permission permission
            JOIN sys_role_permission role_permission ON role_permission.permission_id = permission.id
            JOIN sys_user_role user_role ON user_role.role_id = role_permission.role_id
            JOIN sys_role role ON role.id = user_role.role_id
            WHERE user_role.user_id = #{userId}
              AND role.status = 'ACTIVE'
              AND permission.status = 'ACTIVE'
            ORDER BY permission.code
            """)
    List<String> findPermissionCodes(Long userId);

    @Select("""
            SELECT campus.id
            FROM org_campus campus
            JOIN sys_user_campus user_campus ON user_campus.campus_id = campus.id
            WHERE user_campus.user_id = #{userId}
              AND campus.status = 'ACTIVE'
            ORDER BY user_campus.primary_campus DESC, campus.sort_order, campus.id
            """)
    List<Long> findCampusIds(Long userId);

    @Update("""
            UPDATE sys_user
            SET status = 'ACTIVE',
                failed_login_attempts = 0,
                locked_until = NULL,
                version = version + 1,
                updated_at = #{now}
            WHERE id = #{userId}
              AND status = 'LOCKED'
              AND locked_until <= #{now}
            """)
    int unlockExpiredAccount(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sys_user
            SET status = CASE
                    WHEN failed_login_attempts + 1 >= #{maxAttempts} THEN 'LOCKED'
                    ELSE status
                END,
                locked_until = CASE
                    WHEN failed_login_attempts + 1 >= #{maxAttempts} THEN #{lockedUntil}
                    ELSE locked_until
                END,
                failed_login_attempts = failed_login_attempts + 1,
                version = version + 1,
                updated_at = #{now}
            WHERE id = #{userId}
              AND status = 'ACTIVE'
            """)
    int recordFailedPassword(
            @Param("userId") Long userId,
            @Param("maxAttempts") int maxAttempts,
            @Param("lockedUntil") LocalDateTime lockedUntil,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sys_user
            SET failed_login_attempts = 0,
                locked_until = NULL,
                last_login_at = #{now},
                version = version + 1,
                updated_at = #{now}
            WHERE id = #{userId}
            """)
    int recordSuccessfulLogin(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO sys_login_audit (
                user_id, username_attempted, event_type, success, failure_reason,
                ip_address, user_agent, request_id, occurred_at
            ) VALUES (
                #{userId}, #{username}, #{eventType}, #{success}, #{failureReason},
                #{ipAddress}, #{userAgent}, #{requestId}, #{occurredAt}
            )
            """)
    int insertLoginAudit(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("eventType") String eventType,
            @Param("success") boolean success,
            @Param("failureReason") String failureReason,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("requestId") String requestId,
            @Param("occurredAt") LocalDateTime occurredAt);
}
