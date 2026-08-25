package com.zoomedu.platform.identity;

import java.util.List;

public record UserDirectoryOptions(
        List<UserStatus> statuses,
        List<UserCampusAssignment> campuses) {

    public UserDirectoryOptions {
        statuses = List.copyOf(statuses);
        campuses = List.copyOf(campuses);
    }
}
