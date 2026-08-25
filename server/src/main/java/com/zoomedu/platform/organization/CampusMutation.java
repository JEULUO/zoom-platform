package com.zoomedu.platform.organization;

record CampusMutation(
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
        int sortOrder) {
}
