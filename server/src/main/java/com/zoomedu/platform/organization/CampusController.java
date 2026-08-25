package com.zoomedu.platform.organization;

import com.zoomedu.platform.audit.OperationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/campuses")
class CampusController {

    private final CampusService campusService;

    CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('campus.read')")
    CampusPage findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CampusStatus status,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return campusService.findPage(
                keyword,
                status,
                page,
                pageSize,
                CampusAccessContext.from(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('campus.read')")
    CampusDetail findById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return campusService.findById(id, CampusAccessContext.from(jwt));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('campus.manage')")
    ResponseEntity<CampusDetail> create(
            @Valid @RequestBody CreateCampusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        CampusDetail created = campusService.create(
                request,
                CampusAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
        return ResponseEntity.created(URI.create("/api/v1/campuses/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('campus.manage')")
    CampusDetail update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCampusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        return campusService.update(
                id,
                request,
                CampusAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('campus.manage')")
    CampusDetail updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CampusStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        return campusService.updateStatus(
                id,
                request,
                CampusAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
    }
}
