package com.zoomedu.platform.organization;

import com.zoomedu.platform.audit.OperationAuditService;
import com.zoomedu.platform.audit.OperationContext;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CampusService {

    private final CampusMapper campusMapper;
    private final OperationAuditService operationAuditService;

    CampusService(CampusMapper campusMapper, OperationAuditService operationAuditService) {
        this.campusMapper = campusMapper;
        this.operationAuditService = operationAuditService;
    }

    @Transactional(readOnly = true)
    CampusPage findPage(
            String keyword,
            CampusStatus status,
            int page,
            int pageSize,
            CampusAccessContext access) {
        String normalizedKeyword = trimToNull(keyword);
        int offset = (page - 1) * pageSize;
        return new CampusPage(
                campusMapper.findPage(
                        normalizedKeyword,
                        status,
                        access.hasAllAccess(),
                        access.campusIds(),
                        offset,
                        pageSize),
                page,
                pageSize,
                campusMapper.count(
                        normalizedKeyword,
                        status,
                        access.hasAllAccess(),
                        access.campusIds()));
    }

    @Transactional(readOnly = true)
    CampusDetail findById(Long id, CampusAccessContext access) {
        return findAccessibleCampus(id, access);
    }

    @Transactional
    CampusDetail create(
            CreateCampusRequest request,
            CampusAccessContext access,
            OperationContext operationContext) {
        requireAllAccess(access);
        CampusMutation campus = mutation(request);
        if (campusMapper.codeExists(campus.code())) {
            throw conflict("CAMPUS_CODE_EXISTS", "Campus code already exists");
        }
        try {
            campusMapper.insert(campus, access.userId());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("CAMPUS_CODE_EXISTS", "Campus code already exists");
        }
        CampusDetail created = campusMapper.findByCode(campus.code());
        operationAuditService.recordSuccess(
                operationContext,
                "campus",
                "CAMPUS_CREATE",
                "CAMPUS",
                created.id().toString(),
                Map.of("code", created.code(), "name", created.name()));
        return created;
    }

    @Transactional
    CampusDetail update(
            Long id,
            UpdateCampusRequest request,
            CampusAccessContext access,
            OperationContext operationContext) {
        CampusDetail existing = findAccessibleCampus(id, access);
        CampusMutation campus = mutation(existing.code(), request);
        if (campusMapper.update(id, campus, request.version(), access.userId()) == 0) {
            throw conflict("CAMPUS_VERSION_CONFLICT", "Campus was modified by another request");
        }
        CampusDetail updated = campusMapper.findById(id);
        operationAuditService.recordSuccess(
                operationContext,
                "campus",
                "CAMPUS_UPDATE",
                "CAMPUS",
                id.toString(),
                Map.of("code", updated.code(), "name", updated.name()));
        return updated;
    }

    @Transactional
    CampusDetail updateStatus(
            Long id,
            CampusStatusRequest request,
            CampusAccessContext access,
            OperationContext operationContext) {
        CampusDetail existing = findAccessibleCampus(id, access);
        if (existing.version() != request.version()) {
            throw conflict("CAMPUS_VERSION_CONFLICT", "Campus was modified by another request");
        }
        if (existing.status() == request.status()) {
            return existing;
        }
        if (campusMapper.updateStatus(id, request.status(), request.version(), access.userId()) == 0) {
            throw conflict("CAMPUS_VERSION_CONFLICT", "Campus was modified by another request");
        }
        CampusDetail updated = campusMapper.findById(id);
        operationAuditService.recordSuccess(
                operationContext,
                "campus",
                "CAMPUS_STATUS_CHANGE",
                "CAMPUS",
                id.toString(),
                Map.of("code", updated.code(), "status", updated.status().name()));
        return updated;
    }

    private CampusDetail findAccessibleCampus(Long id, CampusAccessContext access) {
        if (!access.canAccess(id)) {
            throw notFound();
        }
        CampusDetail campus = campusMapper.findById(id);
        if (campus == null) {
            throw notFound();
        }
        return campus;
    }

    private CampusMutation mutation(CreateCampusRequest request) {
        return new CampusMutation(
                request.code().trim().toUpperCase(Locale.ROOT),
                request.name().trim(),
                trimToNull(request.legalName()),
                validateTimezone(request.timezone()),
                request.countryCode().trim().toUpperCase(Locale.ROOT),
                trimToNull(request.addressLine1()),
                trimToNull(request.addressLine2()),
                trimToNull(request.city()),
                trimToNull(request.postalCode()),
                lowerToNull(request.contactEmail()),
                trimToNull(request.contactPhone()),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private CampusMutation mutation(String code, UpdateCampusRequest request) {
        return new CampusMutation(
                code,
                request.name().trim(),
                trimToNull(request.legalName()),
                validateTimezone(request.timezone()),
                request.countryCode().trim().toUpperCase(Locale.ROOT),
                trimToNull(request.addressLine1()),
                trimToNull(request.addressLine2()),
                trimToNull(request.city()),
                trimToNull(request.postalCode()),
                lowerToNull(request.contactEmail()),
                trimToNull(request.contactPhone()),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private String validateTimezone(String timezone) {
        String normalized = timezone.trim();
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (DateTimeException exception) {
            throw new CampusException(
                    "INVALID_TIMEZONE",
                    "Timezone must be a valid IANA zone ID",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void requireAllAccess(CampusAccessContext access) {
        if (!access.hasAllAccess()) {
            throw new AccessDeniedException("Creating campuses requires ALL data scope");
        }
    }

    private CampusException notFound() {
        return new CampusException("CAMPUS_NOT_FOUND", "Campus was not found", HttpStatus.NOT_FOUND);
    }

    private CampusException conflict(String code, String message) {
        return new CampusException(code, message, HttpStatus.CONFLICT);
    }

    private String lowerToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
