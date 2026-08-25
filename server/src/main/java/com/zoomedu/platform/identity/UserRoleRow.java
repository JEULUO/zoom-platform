package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.DataScope;

record UserRoleRow(
        Long userId,
        String code,
        String name,
        DataScope dataScope) {
}
