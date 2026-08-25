package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.DataScope;

public record UserRoleAssignment(
        String code,
        String name,
        DataScope dataScope) {
}
