package com.zoomedu.platform.identity;

import com.zoomedu.platform.organization.CampusStatus;

record UserCampusRow(
        Long userId,
        Long id,
        String code,
        String name,
        boolean primaryCampus,
        CampusStatus status) {
}
