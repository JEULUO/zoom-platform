package com.zoomedu.platform.organization;

import java.time.LocalDateTime;

public record CampusDetail(
        Long id,
        String code,
        String name,
        String legalName,
        String timezone,
        String countryCode,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String contactEmail,
        String contactPhone,
        CampusStatus status,
        int sortOrder,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
