package com.zoomedu.platform.organization;

import java.util.List;

public record CampusPage(
        List<CampusSummary> items,
        int page,
        int pageSize,
        long total) {

    public CampusPage {
        items = List.copyOf(items);
    }
}
