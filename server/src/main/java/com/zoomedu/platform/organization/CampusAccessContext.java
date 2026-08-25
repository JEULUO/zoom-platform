package com.zoomedu.platform.organization;

import com.zoomedu.platform.auth.DataScope;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

record CampusAccessContext(
        Long userId,
        String username,
        DataScope dataScope,
        List<Long> campusIds) {

    static CampusAccessContext from(Jwt jwt) {
        return new CampusAccessContext(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                DataScope.valueOf(jwt.getClaimAsString("dataScope")),
                readCampusIds(jwt.getClaim("campusIds")));
    }

    boolean hasAllAccess() {
        return dataScope == DataScope.ALL;
    }

    boolean canAccess(Long campusId) {
        return hasAllAccess() || campusIds.contains(campusId);
    }

    private static List<Long> readCampusIds(Object claim) {
        if (!(claim instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(CampusAccessContext::asLong).toList();
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
