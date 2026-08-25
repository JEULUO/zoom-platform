package com.zoomedu.platform.identity;

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
@RequestMapping("/api/v1/users")
class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    UserDirectoryController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    UserPage findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long campusId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return userDirectoryService.findPage(
                keyword,
                status,
                campusId,
                page,
                pageSize,
                UserAccessContext.from(jwt));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('user.read')")
    UserDirectoryOptions findOptions(@AuthenticationPrincipal Jwt jwt) {
        return userDirectoryService.findOptions(UserAccessContext.from(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user.read')")
    UserDetail findById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return userDirectoryService.findById(id, UserAccessContext.from(jwt));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user.manage')")
    ResponseEntity<UserDetail> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        UserDetail created = userDirectoryService.create(
                request,
                UserAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user.manage')")
    UserDetail update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        return userDirectoryService.update(
                id,
                request,
                UserAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user.manage')")
    UserDetail updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        return userDirectoryService.updateStatus(
                id,
                request,
                UserAccessContext.from(jwt),
                OperationContext.from(jwt, httpRequest));
    }
}
