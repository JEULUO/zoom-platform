package com.zoomedu.platform.auth;

import java.util.Collection;

public enum DataScope {
    SELF,
    ASSIGNED_CAMPUSES,
    ALL;

    static DataScope broadest(Collection<String> scopes) {
        if (scopes.contains(ALL.name())) {
            return ALL;
        }
        if (scopes.contains(ASSIGNED_CAMPUSES.name())) {
            return ASSIGNED_CAMPUSES;
        }
        return SELF;
    }
}
