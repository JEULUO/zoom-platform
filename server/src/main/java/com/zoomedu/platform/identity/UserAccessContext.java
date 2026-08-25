package com.zoomedu.platform.identity;

import com.zoomedu.platform.auth.DataScope;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

record UserAccessContext(
        Long userId,
        DataScope dataScope,
        List<Long> campusIds) {

    static UserAccessContext from(Jwt jwt) {
        return new UserAccessContext(
                Long.valueOf(jwt.getSubject()),
                DataScope.valueOf(jwt.getClaimAsString("dataScope")),
                readCampusIds(jwt.getClaim("campusIds")));
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
}
