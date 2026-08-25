package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.DataScope;

public record UserRoleOption(
        Long id,
        String code,
        String name,
        DataScope dataScope,
        int sortOrder) {
}
