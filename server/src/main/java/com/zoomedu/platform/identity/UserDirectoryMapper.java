package com.zoomedu.platform.identity;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface UserDirectoryMapper {

    @Select("""
            <script>
            SELECT account.id, account.username, account.display_name, account.email, account.phone,
                   account.preferred_language, account.timezone, account.status,
                   account.failed_login_attempts, account.locked_until, account.last_login_at,
                   account.password_changed_at, account.version, account.created_at, account.updated_at
            FROM sys_user account
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        UPPER(account.username) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(account.display_name) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(account.email, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(account.phone, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                    )
                </if>
                <if test="status != null">
                    AND account.status = #{status}
                </if>
                <if test="campusId != null">
                    AND EXISTS (
                        SELECT 1 FROM sys_user_campus campus_filter
                        WHERE campus_filter.user_id = account.id
                          AND campus_filter.campus_id = #{campusId}
                    )
                </if>
                <if test="!allAccess">
                    <choose>
                        <when test="assignedAccess and campusIds != null and campusIds.size() > 0">
                            AND EXISTS (
                                SELECT 1 FROM sys_user_campus scope_filter
                                WHERE scope_filter.user_id = account.id
                                  AND scope_filter.campus_id IN
                                  <foreach collection="campusIds" item="scopeCampusId" open="(" separator="," close=")">
                                      #{scopeCampusId}
                                  </foreach>
                            )
                        </when>
                        <otherwise>
                            AND account.id = #{actorUserId}
                        </otherwise>
                    </choose>
                </if>
            </where>
            ORDER BY account.display_name, account.username, account.id
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<UserRow> findPage(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("campusId") Long campusId,
            @Param("allAccess") boolean allAccess,
            @Param("assignedAccess") boolean assignedAccess,
            @Param("actorUserId") Long actorUserId,
            @Param("campusIds") List<Long> campusIds,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_user account
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        UPPER(account.username) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(account.display_name) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(account.email, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(account.phone, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                    )
                </if>
                <if test="status != null">
                    AND account.status = #{status}
                </if>
                <if test="campusId != null">
                    AND EXISTS (
                        SELECT 1 FROM sys_user_campus campus_filter
                        WHERE campus_filter.user_id = account.id
                          AND campus_filter.campus_id = #{campusId}
                    )
                </if>
                <if test="!allAccess">
                    <choose>
                        <when test="assignedAccess and campusIds != null and campusIds.size() > 0">
                            AND EXISTS (
                                SELECT 1 FROM sys_user_campus scope_filter
                                WHERE scope_filter.user_id = account.id
                                  AND scope_filter.campus_id IN
                                  <foreach collection="campusIds" item="scopeCampusId" open="(" separator="," close=")">
                                      #{scopeCampusId}
                                  </foreach>
                            )
                        </when>
                        <otherwise>
                            AND account.id = #{actorUserId}
                        </otherwise>
                    </choose>
                </if>
            </where>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("campusId") Long campusId,
            @Param("allAccess") boolean allAccess,
            @Param("assignedAccess") boolean assignedAccess,
            @Param("actorUserId") Long actorUserId,
            @Param("campusIds") List<Long> campusIds);

    @Select("""
            SELECT id, username, display_name, email, phone, preferred_language, timezone,
                   status, failed_login_attempts, locked_until, last_login_at,
                   password_changed_at, version, created_at, updated_at
            FROM sys_user
            WHERE id = #{id}
            """)
    UserRow findById(Long id);

    @Select("""
            SELECT id, username, display_name, email, phone, preferred_language, timezone,
                   status, failed_login_attempts, locked_until, last_login_at,
                   password_changed_at, version, created_at, updated_at
            FROM sys_user
            WHERE username = #{username}
            """)
    UserRow findByUsername(String username);

    @Select("""
            <script>
            SELECT COUNT(*) > 0
            FROM sys_user_campus
            WHERE user_id = #{userId}
              AND campus_id IN
              <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">
                  #{campusId}
              </foreach>
            </script>
            """)
    boolean sharesCampus(
            @Param("userId") Long userId,
            @Param("campusIds") List<Long> campusIds);

    @Select("""
            <script>
            SELECT user_role.user_id, role.code, role.name, role.data_scope
            FROM sys_user_role user_role
            JOIN sys_role role ON role.id = user_role.role_id
            WHERE user_role.user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
              </foreach>
            ORDER BY role.sort_order, role.name, role.id
            </script>
            """)
    List<UserRoleRow> findRoles(@Param("userIds") List<Long> userIds);

    @Select("""
            <script>
            SELECT user_campus.user_id, campus.id, campus.code, campus.name,
                   user_campus.primary_campus, campus.status
            FROM sys_user_campus user_campus
            JOIN org_campus campus ON campus.id = user_campus.campus_id
            WHERE user_campus.user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
              </foreach>
              <if test="!allAccess">
                  <choose>
                      <when test="campusIds != null and campusIds.size() > 0">
                          AND campus.id IN
                          <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">
                              #{campusId}
                          </foreach>
                      </when>
                      <otherwise>
                          AND 1 = 0
                      </otherwise>
                  </choose>
              </if>
            ORDER BY user_campus.primary_campus DESC, campus.sort_order, campus.name, campus.id
            </script>
            """)
    List<UserCampusRow> findCampuses(
            @Param("userIds") List<Long> userIds,
            @Param("allAccess") boolean allAccess,
            @Param("campusIds") List<Long> campusIds);

    @Select("""
            <script>
            SELECT NULL AS user_id, campus.id, campus.code, campus.name,
                   FALSE AS primary_campus, campus.status
            FROM org_campus campus
            <where>
                AND campus.status = 'ACTIVE'
                <if test="!allAccess">
                    <choose>
                        <when test="campusIds != null and campusIds.size() > 0">
                            AND campus.id IN
                            <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">
                                #{campusId}
                            </foreach>
                        </when>
                        <otherwise>
                            AND 1 = 0
                        </otherwise>
                    </choose>
                </if>
            </where>
            ORDER BY campus.sort_order, campus.name, campus.id
            </script>
            """)
    List<UserCampusRow> findCampusOptions(
            @Param("allAccess") boolean allAccess,
            @Param("campusIds") List<Long> campusIds);

    @Select("""
            SELECT id, code, name, data_scope, sort_order
            FROM sys_role
            WHERE status = 'ACTIVE'
            ORDER BY sort_order, name, id
            """)
    List<UserRoleOption> findRoleOptions();

    @Select("""
            <script>
            SELECT id, code, name, data_scope, sort_order
            FROM sys_role
            WHERE id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                  #{roleId}
              </foreach>
              AND status = 'ACTIVE'
            ORDER BY sort_order, name, id
            </script>
            """)
    List<UserRoleOption> findRolesByIds(@Param("roleIds") List<Long> roleIds);

    @Select("""
            SELECT role.id, role.code, role.name, role.data_scope, role.sort_order
            FROM sys_role role
            JOIN sys_user_role user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = #{userId}
            ORDER BY role.sort_order, role.name, role.id
            """)
    List<UserRoleOption> findUserRoleOptions(Long userId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_user_campus
            WHERE user_id = #{userId}
              AND campus_id NOT IN
              <foreach collection="campusIds" item="campusId" open="(" separator="," close=")">
                  #{campusId}
              </foreach>
            </script>
            """)
    long countInaccessibleCampuses(
            @Param("userId") Long userId,
            @Param("campusIds") List<Long> campusIds);

    @Insert("""
            INSERT INTO sys_user (
                username, password_hash, display_name, email, phone,
                preferred_language, timezone, status, created_by, updated_by
            ) VALUES (
                #{user.username}, #{user.passwordHash}, #{user.displayName}, #{user.email}, #{user.phone},
                #{user.preferredLanguage}, #{user.timezone}, #{user.status}, #{actorUserId}, #{actorUserId}
            )
            """)
    int insertUser(@Param("user") UserMutation user, @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE sys_user
            SET display_name = #{user.displayName},
                email = #{user.email},
                phone = #{user.phone},
                preferred_language = #{user.preferredLanguage},
                timezone = #{user.timezone},
                updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP(6),
                version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int updateUser(
            @Param("id") Long id,
            @Param("user") UserMutation user,
            @Param("version") int version,
            @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE sys_user
            SET status = #{status},
                failed_login_attempts = 0,
                locked_until = NULL,
                updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP(6),
                version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int updateUserStatus(
            @Param("id") Long id,
            @Param("status") UserStatus status,
            @Param("version") int version,
            @Param("actorUserId") Long actorUserId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(Long userId);

    @Insert("""
            <script>
            INSERT INTO sys_user_role (user_id, role_id, assigned_by)
            VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId}, #{actorUserId})
            </foreach>
            </script>
            """)
    int insertUserRoles(
            @Param("userId") Long userId,
            @Param("roleIds") List<Long> roleIds,
            @Param("actorUserId") Long actorUserId);

    @Delete("DELETE FROM sys_user_campus WHERE user_id = #{userId}")
    int deleteUserCampuses(Long userId);

    @Insert("""
            <script>
            INSERT INTO sys_user_campus (user_id, campus_id, primary_campus, assigned_by)
            VALUES
            <foreach collection="campusIds" item="campusId" separator=",">
                (#{userId}, #{campusId}, #{campusId} = #{primaryCampusId}, #{actorUserId})
            </foreach>
            </script>
            """)
    int insertUserCampuses(
            @Param("userId") Long userId,
            @Param("campusIds") List<Long> campusIds,
            @Param("primaryCampusId") Long primaryCampusId,
            @Param("actorUserId") Long actorUserId);
}
