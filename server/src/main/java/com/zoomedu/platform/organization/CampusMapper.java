package com.zoomedu.platform.organization;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CampusMapper {

    @Select("""
            SELECT id, code, name, city, timezone, country_code, status,
                   sort_order, version, updated_at
            FROM org_campus
            WHERE status = 'ACTIVE'
            ORDER BY sort_order, name, id
            """)
    List<CampusSummary> findActiveCampuses();

    @Select("""
            <script>
            SELECT id, code, name, city, timezone, country_code, status,
                   sort_order, version, updated_at
            FROM org_campus
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        UPPER(code) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(name) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(city, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                    )
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
                <if test="!allAccess">
                    <choose>
                        <when test="campusIds != null and campusIds.size() > 0">
                            AND id IN
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
            ORDER BY sort_order, name, id
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<CampusSummary> findPage(
            @Param("keyword") String keyword,
            @Param("status") CampusStatus status,
            @Param("allAccess") boolean allAccess,
            @Param("campusIds") List<Long> campusIds,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM org_campus
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (
                        UPPER(code) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(name) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                        OR UPPER(COALESCE(city, '')) LIKE CONCAT('%', UPPER(#{keyword}), '%')
                    )
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
                <if test="!allAccess">
                    <choose>
                        <when test="campusIds != null and campusIds.size() > 0">
                            AND id IN
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
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") CampusStatus status,
            @Param("allAccess") boolean allAccess,
            @Param("campusIds") List<Long> campusIds);

    @Select("""
            SELECT id, code, name, legal_name, timezone, country_code,
                   address_line_1, address_line_2, city, postal_code,
                   contact_email, contact_phone, status, sort_order, version,
                   created_at, updated_at
            FROM org_campus
            WHERE id = #{id}
            """)
    CampusDetail findById(Long id);

    @Select("""
            SELECT id, code, name, legal_name, timezone, country_code,
                   address_line_1, address_line_2, city, postal_code,
                   contact_email, contact_phone, status, sort_order, version,
                   created_at, updated_at
            FROM org_campus
            WHERE code = #{code}
            """)
    CampusDetail findByCode(String code);

    @Select("SELECT COUNT(*) > 0 FROM org_campus WHERE code = #{code}")
    boolean codeExists(String code);

    @Insert("""
            INSERT INTO org_campus (
                code, name, legal_name, timezone, country_code,
                address_line_1, address_line_2, city, postal_code,
                contact_email, contact_phone, sort_order, created_by, updated_by
            ) VALUES (
                #{campus.code}, #{campus.name}, #{campus.legalName}, #{campus.timezone},
                #{campus.countryCode}, #{campus.addressLine1}, #{campus.addressLine2},
                #{campus.city}, #{campus.postalCode}, #{campus.contactEmail},
                #{campus.contactPhone}, #{campus.sortOrder}, #{userId}, #{userId}
            )
            """)
    int insert(@Param("campus") CampusMutation campus, @Param("userId") Long userId);

    @Update("""
            UPDATE org_campus
            SET name = #{campus.name},
                legal_name = #{campus.legalName},
                timezone = #{campus.timezone},
                country_code = #{campus.countryCode},
                address_line_1 = #{campus.addressLine1},
                address_line_2 = #{campus.addressLine2},
                city = #{campus.city},
                postal_code = #{campus.postalCode},
                contact_email = #{campus.contactEmail},
                contact_phone = #{campus.contactPhone},
                sort_order = #{campus.sortOrder},
                updated_by = #{userId},
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int update(
            @Param("id") Long id,
            @Param("campus") CampusMutation campus,
            @Param("version") int version,
            @Param("userId") Long userId);

    @Update("""
            UPDATE org_campus
            SET status = #{status},
                updated_by = #{userId},
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int updateStatus(
            @Param("id") Long id,
            @Param("status") CampusStatus status,
            @Param("version") int version,
            @Param("userId") Long userId);
}
