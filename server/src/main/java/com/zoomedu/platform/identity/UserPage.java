package com.zoomedu.platform.identity;

import java.util.List;

public record UserPage(
        List<UserSummary> items,
        int page,
        int pageSize,
        long total) {

    public UserPage {
        items = List.copyOf(items);
    }
}
