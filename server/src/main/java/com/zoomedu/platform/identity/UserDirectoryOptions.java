package com.zoomedu.platform.identity;

import java.util.List;

public record UserDirectoryOptions(
        List<UserStatus> statuses,
        List<UserCampusAssignment> campuses,
        List<UserRoleOption> roles) {

    public UserDirectoryOptions {
        statuses = List.copyOf(statuses);
        campuses = List.copyOf(campuses);
        roles = List.copyOf(roles);
    }
}
