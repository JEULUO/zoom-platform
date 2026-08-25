package com.zoomedu.platform.identity;

import com.zoomedu.platform.organization.CampusStatus;

public record UserCampusAssignment(
        Long id,
        String code,
        String name,
        boolean primaryCampus,
        CampusStatus status) {
}
