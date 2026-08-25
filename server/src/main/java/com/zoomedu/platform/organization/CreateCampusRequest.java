package com.zoomedu.platform.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCampusRequest(
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, numbers, underscores, or hyphens")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 160) String legalName,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @Size(max = 160) String addressLine1,
        @Size(max = 160) String addressLine2,
        @Size(max = 80) String city,
        @Size(max = 20) String postalCode,
        @Email @Size(max = 160) String contactEmail,
        @Size(max = 32) String contactPhone,
        @Min(0) @Max(100000) Integer sortOrder) {
}
