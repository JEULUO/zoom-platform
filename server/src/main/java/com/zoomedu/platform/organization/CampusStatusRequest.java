package com.zoomedu.platform.organization;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CampusStatusRequest(
        @NotNull CampusStatus status,
        @NotNull @Min(0) Integer version) {
}
