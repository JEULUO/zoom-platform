package com.zoomedu.platform.identity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService {

    private final UserDirectoryMapper userDirectoryMapper;

    UserDirectoryService(UserDirectoryMapper userDirectoryMapper) {
        this.userDirectoryMapper = userDirectoryMapper;
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
        return new UserDirectoryOptions(List.of(UserStatus.values()), campuses);
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

    private UserDirectoryException notFound() {
        return new UserDirectoryException(
                "USER_NOT_FOUND",
                "User was not found",
                HttpStatus.NOT_FOUND);
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
