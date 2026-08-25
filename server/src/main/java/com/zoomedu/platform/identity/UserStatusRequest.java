package com.zoomedu.platform.identity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull UserStatus status,
        @Min(0) int version) {
}
