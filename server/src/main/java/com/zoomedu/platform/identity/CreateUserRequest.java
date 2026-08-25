package com.zoomedu.platform.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(min = 8, max = 72) String initialPassword,
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 160) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 16) String preferredLanguage,
        @NotBlank @Size(max = 64) String timezone,
        UserStatus status,
        @NotEmpty List<Long> roleIds,
        List<Long> campusIds,
        Long primaryCampusId) {
}
