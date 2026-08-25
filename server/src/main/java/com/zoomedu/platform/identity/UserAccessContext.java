package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.DataScope;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

record UserAccessContext(
        Long userId,
        DataScope dataScope,
        List<Long> campusIds,
        List<String> roleCodes,
        List<String> permissionCodes) {

    static UserAccessContext from(Jwt jwt) {
        return new UserAccessContext(
                Long.valueOf(jwt.getSubject()),
                DataScope.valueOf(jwt.getClaimAsString("dataScope")),
                readCampusIds(jwt.getClaim("campusIds")),
                readStrings(jwt.getClaim("roles")),
                readStrings(jwt.getClaim("permissions")));
    }

    boolean hasAllAccess() {
        return dataScope == DataScope.ALL;
    }

    boolean hasAssignedAccess() {
        return dataScope == DataScope.ASSIGNED_CAMPUSES;
    }

    boolean canFilterCampus(Long campusId) {
        return campusId == null || hasAllAccess() || campusIds.contains(campusId);
    }

    boolean canManageUsers() {
        return permissionCodes.contains("user.manage");
    }

    private static List<Long> readCampusIds(Object claim) {
        if (!(claim instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(UserAccessContext::asLong).toList();
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static List<String> readStrings(Object claim) {
        if (!(claim instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }
}
