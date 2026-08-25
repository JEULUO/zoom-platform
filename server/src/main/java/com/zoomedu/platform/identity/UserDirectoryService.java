package com.zoomedu.platform.identity;

import com.zoomedu.platform.audit.OperationAuditService;
import com.zoomedu.platform.audit.OperationContext;
import com.zoomedu.platform.auth.DataScope;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService {

    private final UserDirectoryMapper userDirectoryMapper;
    private final OperationAuditService operationAuditService;
    private final PasswordEncoder passwordEncoder;

    UserDirectoryService(
            UserDirectoryMapper userDirectoryMapper,
            OperationAuditService operationAuditService,
            PasswordEncoder passwordEncoder) {
        this.userDirectoryMapper = userDirectoryMapper;
        this.operationAuditService = operationAuditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    UserPage findPage(
            String keyword,
            UserStatus status,
            Long campusId,
            int page,
            int pageSize,
            UserAccessContext access) {
        if (!access.canFilterCampus(campusId)) {
            return new UserPage(List.of(), page, pageSize, 0);
        }
        String normalizedKeyword = trimToNull(keyword);
        int offset = (page - 1) * pageSize;
        List<UserRow> rows = userDirectoryMapper.findPage(
                normalizedKeyword,
                status,
                campusId,
                access.hasAllAccess(),
                access.hasAssignedAccess(),
                access.userId(),
                access.campusIds(),
                offset,
                pageSize);
        UserAssociations associations = associations(rows, access);
        return new UserPage(
                rows.stream().map(row -> summary(row, associations)).toList(),
                page,
                pageSize,
                userDirectoryMapper.count(
                        normalizedKeyword,
                        status,
                        campusId,
                        access.hasAllAccess(),
                        access.hasAssignedAccess(),
                        access.userId(),
                        access.campusIds()));
    }

    @Transactional(readOnly = true)
    UserDetail findById(Long id, UserAccessContext access) {
        requireAccessible(id, access);
        UserRow row = userDirectoryMapper.findById(id);
        if (row == null) {
            throw notFound();
        }
        UserAssociations associations = associations(List.of(row), access);
        return detail(row, associations);
    }

    @Transactional(readOnly = true)
    UserDirectoryOptions findOptions(UserAccessContext access) {
        List<UserCampusAssignment> campuses = userDirectoryMapper
                .findCampusOptions(access.hasAllAccess(), access.campusIds())
                .stream()
                .map(this::campusAssignment)
                .toList();
        List<UserRoleOption> roles = access.canManageUsers()
                ? grantableRoles(access)
                : List.of();
        return new UserDirectoryOptions(List.of(UserStatus.values()), campuses, roles);
    }

    @Transactional
    UserDetail create(
            CreateUserRequest request,
            UserAccessContext access,
            OperationContext operationContext) {
        requireManager(access);
        List<UserRoleOption> roles = validateRoleAssignments(request.roleIds(), access);
        AssignmentPlan campuses = validateCampusAssignments(
                request.campusIds(), request.primaryCampusId(), roles, access);
        UserMutation user = createMutation(request);
        try {
            userDirectoryMapper.insertUser(user, access.userId());
            UserRow created = userDirectoryMapper.findByUsername(user.username());
            replaceAssignments(created.id(), request.roleIds(), campuses, access.userId());
            operationAuditService.recordSuccess(
                    operationContext,
                    "identity",
                    "USER_CREATE",
                    "USER",
                    created.id().toString(),
                    Map.of("username", created.username(), "status", created.status().name()));
            return findById(created.id(), access);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("USER_IDENTIFIER_EXISTS", "Username, email, or phone already exists");
        }
    }

    @Transactional
    UserDetail update(
            Long id,
            UpdateUserRequest request,
            UserAccessContext access,
            OperationContext operationContext) {
        if (id.equals(access.userId())) {
            throw invalid("USER_SELF_MANAGE_FORBIDDEN", "Use the personal profile flow to update your own account");
        }
        UserRow existing = requireManageable(id, access);
        List<UserRoleOption> roles = validateRoleAssignments(request.roleIds(), access);
        AssignmentPlan campuses = validateCampusAssignments(
                request.campusIds(), request.primaryCampusId(), roles, access);
        UserMutation user = updateMutation(existing.username(), existing.status(), request);
        try {
            if (userDirectoryMapper.updateUser(id, user, request.version(), access.userId()) == 0) {
                throw conflict("USER_VERSION_CONFLICT", "User was modified by another request");
            }
            replaceAssignments(id, request.roleIds(), campuses, access.userId());
            operationAuditService.recordSuccess(
                    operationContext,
                    "identity",
                    "USER_UPDATE",
                    "USER",
                    id.toString(),
                    Map.of("username", existing.username()));
            return findById(id, access);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("USER_IDENTIFIER_EXISTS", "Username, email, or phone already exists");
        }
    }

    @Transactional
    UserDetail updateStatus(
            Long id,
            UserStatusRequest request,
            UserAccessContext access,
            OperationContext operationContext) {
        UserRow existing = requireManageable(id, access);
        if (id.equals(access.userId())) {
            throw new UserDirectoryException(
                    "USER_SELF_STATUS_FORBIDDEN",
                    "You cannot change your own account status",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.status() != UserStatus.ACTIVE && request.status() != UserStatus.DISABLED) {
            throw invalid("USER_STATUS_INVALID", "Only ACTIVE and DISABLED can be assigned manually");
        }
        if (existing.version() != request.version()) {
            throw conflict("USER_VERSION_CONFLICT", "User was modified by another request");
        }
        if (existing.status() == request.status()) {
            return findById(id, access);
        }
        if (userDirectoryMapper.updateUserStatus(
                id, request.status(), request.version(), access.userId()) == 0) {
            throw conflict("USER_VERSION_CONFLICT", "User was modified by another request");
        }
        operationAuditService.recordSuccess(
                operationContext,
                "identity",
                "USER_STATUS_CHANGE",
                "USER",
                id.toString(),
                Map.of("username", existing.username(), "status", request.status().name()));
        return findById(id, access);
    }

    private UserRow requireManageable(Long id, UserAccessContext access) {
        requireManager(access);
        requireAccessible(id, access);
        UserRow user = userDirectoryMapper.findById(id);
        if (user == null) {
            throw notFound();
        }
        int actorRank = actorRank(access);
        List<UserRoleOption> targetRoles = userDirectoryMapper.findUserRoleOptions(id);
        if (targetRoles.stream().anyMatch(role -> role.sortOrder() < actorRank)
                || (!access.hasAllAccess()
                        && targetRoles.stream().anyMatch(role -> role.dataScope() == DataScope.ALL))) {
            throw new AccessDeniedException("You cannot manage a user with higher authority");
        }
        if (!access.hasAllAccess()
                && (access.campusIds().isEmpty()
                        || userDirectoryMapper.countInaccessibleCampuses(id, access.campusIds()) > 0)) {
            throw new AccessDeniedException("You cannot replace assignments outside your campus scope");
        }
        return user;
    }

    private void requireManager(UserAccessContext access) {
        if (!access.canManageUsers() || (!access.hasAllAccess() && !access.hasAssignedAccess())) {
            throw new AccessDeniedException("Managing users requires an administrative data scope");
        }
    }

    private List<UserRoleOption> grantableRoles(UserAccessContext access) {
        int actorRank = actorRank(access);
        return userDirectoryMapper.findRoleOptions().stream()
                .filter(role -> role.sortOrder() >= actorRank)
                .filter(role -> access.hasAllAccess() || role.dataScope() != DataScope.ALL)
                .toList();
    }

    private int actorRank(UserAccessContext access) {
        return userDirectoryMapper.findRoleOptions().stream()
                .filter(role -> access.roleCodes().contains(role.code()))
                .mapToInt(UserRoleOption::sortOrder)
                .min()
                .orElseThrow(() -> new AccessDeniedException("Administrative role was not found"));
    }

    private List<UserRoleOption> validateRoleAssignments(List<Long> requestedIds, UserAccessContext access) {
        List<Long> roleIds = distinctIds(requestedIds);
        if (roleIds.isEmpty()) {
            throw invalid("USER_ROLE_REQUIRED", "At least one role is required");
        }
        List<UserRoleOption> requested = userDirectoryMapper.findRolesByIds(roleIds);
        List<Long> grantableIds = grantableRoles(access).stream().map(UserRoleOption::id).toList();
        if (requested.size() != roleIds.size()
                || requested.stream().anyMatch(role -> !grantableIds.contains(role.id()))) {
            throw new AccessDeniedException("One or more roles cannot be granted by this operator");
        }
        return requested;
    }

    private AssignmentPlan validateCampusAssignments(
            List<Long> requestedIds,
            Long requestedPrimaryId,
            List<UserRoleOption> roles,
            UserAccessContext access) {
        List<Long> campusIds = distinctIds(requestedIds);
        if ((!access.hasAllAccess()
                        || roles.stream().anyMatch(role -> role.dataScope() == DataScope.ASSIGNED_CAMPUSES))
                && campusIds.isEmpty()) {
            throw invalid("USER_CAMPUS_REQUIRED", "Assigned-campus roles require at least one campus");
        }
        List<UserCampusAssignment> options = userDirectoryMapper
                .findCampusOptions(access.hasAllAccess(), access.campusIds())
                .stream()
                .map(this::campusAssignment)
                .toList();
        List<Long> allowedIds = options.stream().map(UserCampusAssignment::id).toList();
        if (campusIds.stream().anyMatch(id -> !allowedIds.contains(id))) {
            throw new AccessDeniedException("One or more campuses are outside your active scope");
        }
        Long primaryCampusId = requestedPrimaryId;
        if (campusIds.isEmpty()) {
            if (primaryCampusId != null) {
                throw invalid("USER_PRIMARY_CAMPUS_INVALID", "Primary campus must be assigned to the user");
            }
        } else {
            primaryCampusId = primaryCampusId == null ? campusIds.get(0) : primaryCampusId;
            if (!campusIds.contains(primaryCampusId)) {
                throw invalid("USER_PRIMARY_CAMPUS_INVALID", "Primary campus must be assigned to the user");
            }
        }
        return new AssignmentPlan(campusIds, primaryCampusId);
    }

    private void replaceAssignments(
            Long userId,
            List<Long> requestedRoleIds,
            AssignmentPlan campuses,
            Long actorUserId) {
        List<Long> roleIds = distinctIds(requestedRoleIds);
        userDirectoryMapper.deleteUserRoles(userId);
        userDirectoryMapper.insertUserRoles(userId, roleIds, actorUserId);
        userDirectoryMapper.deleteUserCampuses(userId);
        if (!campuses.campusIds().isEmpty()) {
            userDirectoryMapper.insertUserCampuses(
                    userId, campuses.campusIds(), campuses.primaryCampusId(), actorUserId);
        }
    }

    private UserMutation createMutation(CreateUserRequest request) {
        UserStatus status = request.status() == null ? UserStatus.ACTIVE : request.status();
        if (status == UserStatus.LOCKED || status == UserStatus.DISABLED) {
            throw invalid("USER_STATUS_INVALID", "New users must be ACTIVE or PENDING");
        }
        return new UserMutation(
                request.username().trim().toLowerCase(Locale.ROOT),
                passwordEncoder.encode(request.initialPassword()),
                request.displayName().trim(),
                lowerToNull(request.email()),
                trimToNull(request.phone()),
                request.preferredLanguage().trim(),
                validateTimezone(request.timezone()),
                status);
    }

    private UserMutation updateMutation(String username, UserStatus status, UpdateUserRequest request) {
        return new UserMutation(
                username,
                null,
                request.displayName().trim(),
                lowerToNull(request.email()),
                trimToNull(request.phone()),
                request.preferredLanguage().trim(),
                validateTimezone(request.timezone()),
                status);
    }

    private String validateTimezone(String timezone) {
        String normalized = timezone.trim();
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (DateTimeException exception) {
            throw invalid("INVALID_TIMEZONE", "Timezone must be a valid IANA zone ID");
        }
    }

    private List<Long> distinctIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private void requireAccessible(Long id, UserAccessContext access) {
        if (access.hasAllAccess() || id.equals(access.userId())) {
            return;
        }
        if (access.hasAssignedAccess()
                && !access.campusIds().isEmpty()
                && userDirectoryMapper.sharesCampus(id, access.campusIds())) {
            return;
        }
        throw notFound();
    }

    private UserAssociations associations(List<UserRow> rows, UserAccessContext access) {
        if (rows.isEmpty()) {
            return new UserAssociations(Map.of(), Map.of());
        }
        List<Long> userIds = rows.stream().map(UserRow::id).toList();
        Map<Long, List<UserRoleAssignment>> roles = userDirectoryMapper.findRoles(userIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        UserRoleRow::userId,
                        java.util.stream.Collectors.mapping(this::roleAssignment, java.util.stream.Collectors.toList())));
        Map<Long, List<UserCampusAssignment>> campuses = userDirectoryMapper
                .findCampuses(userIds, access.hasAllAccess(), access.campusIds())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        UserCampusRow::userId,
                        java.util.stream.Collectors.mapping(this::campusAssignment, java.util.stream.Collectors.toList())));
        return new UserAssociations(new HashMap<>(roles), new HashMap<>(campuses));
    }

    private UserSummary summary(UserRow row, UserAssociations associations) {
        return new UserSummary(
                row.id(),
                row.username(),
                row.displayName(),
                row.email(),
                row.phone(),
                row.status(),
                associations.roles(row.id()),
                associations.campuses(row.id()),
                row.lastLoginAt(),
                row.version(),
                row.updatedAt());
    }

    private UserDetail detail(UserRow row, UserAssociations associations) {
        return new UserDetail(
                row.id(),
                row.username(),
                row.displayName(),
                row.email(),
                row.phone(),
                row.preferredLanguage(),
                row.timezone(),
                row.status(),
                row.failedLoginAttempts(),
                row.lockedUntil(),
                row.lastLoginAt(),
                row.passwordChangedAt(),
                associations.roles(row.id()),
                associations.campuses(row.id()),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }

    private UserRoleAssignment roleAssignment(UserRoleRow row) {
        return new UserRoleAssignment(row.code(), row.name(), row.dataScope());
    }

    private UserCampusAssignment campusAssignment(UserCampusRow row) {
        return new UserCampusAssignment(
                row.id(), row.code(), row.name(), row.primaryCampus(), row.status());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String lowerToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private UserDirectoryException notFound() {
        return new UserDirectoryException(
                "USER_NOT_FOUND",
                "User was not found",
                HttpStatus.NOT_FOUND);
    }

    private UserDirectoryException invalid(String code, String message) {
        return new UserDirectoryException(code, message, HttpStatus.BAD_REQUEST);
    }

    private UserDirectoryException conflict(String code, String message) {
        return new UserDirectoryException(code, message, HttpStatus.CONFLICT);
    }

    private record AssignmentPlan(List<Long> campusIds, Long primaryCampusId) {

        AssignmentPlan {
            campusIds = List.copyOf(campusIds);
        }
    }

    private record UserAssociations(
            Map<Long, List<UserRoleAssignment>> rolesByUser,
            Map<Long, List<UserCampusAssignment>> campusesByUser) {

        List<UserRoleAssignment> roles(Long userId) {
            return rolesByUser.getOrDefault(userId, List.of());
        }

        List<UserCampusAssignment> campuses(Long userId) {
            return campusesByUser.getOrDefault(userId, List.of());
        }
    }
}
