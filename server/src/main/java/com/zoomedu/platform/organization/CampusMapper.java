package com.zoomedu.platform.organization;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CampusMapper {

    @Select("""
            SELECT id, code, name, timezone, status
            FROM org_campus
            WHERE status = 'ACTIVE'
            ORDER BY sort_order, name
            """)
    List<CampusSummary> findActiveCampuses();
}
