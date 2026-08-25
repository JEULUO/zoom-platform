package com.zoomedu.platform.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 160) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 16) String preferredLanguage,
        @NotBlank @Size(max = 64) String timezone,
        @NotEmpty List<Long> roleIds,
        List<Long> campusIds,
        Long primaryCampusId,
        @Min(0) int version) {
}
