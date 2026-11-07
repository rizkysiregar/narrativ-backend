package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AdminAuditLogDTO;
import com.rizkysiregar.feedzmore.model.entity.AdminAuditLogEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.request.AuditLogSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.AdminAuditLogRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.ClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private AuditLogService auditLogService;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private UUID authenticateAsAdmin() {
        UserEntity admin = new UserEntity();
        UUID adminId = UUID.randomUUID();
        admin.setId(adminId);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return adminId;
    }

    @Test
    void record_savesEntryWithCurrentAdminAndClientIp() {
        UUID adminId = authenticateAsAdmin();
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.7");

        auditLogService.record(AdminAuditAction.USER_UPDATE, AuditTargetType.USER, "target-1",
                "because", Map.of("fields", List.of("email")));

        ArgumentCaptor<AdminAuditLogEntity> captor = ArgumentCaptor.forClass(AdminAuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AdminAuditLogEntity saved = captor.getValue();
        assertEquals(adminId, saved.getAdminId());
        assertEquals(AdminAuditAction.USER_UPDATE, saved.getAction());
        assertEquals(AuditTargetType.USER, saved.getTargetType());
        assertEquals("target-1", saved.getTargetId());
        assertEquals("because", saved.getReason());
        assertEquals("203.0.113.7", saved.getIp());
        assertEquals(Map.of("fields", List.of("email")), saved.getMetadata());
    }

    @Test
    void record_withoutRequestContext_leavesIpEmpty() {
        authenticateAsAdmin();

        auditLogService.record(AdminAuditAction.USER_RESTORE, AuditTargetType.USER, "target-1", null, null);

        ArgumentCaptor<AdminAuditLogEntity> captor = ArgumentCaptor.forClass(AdminAuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getIp());
        verifyNoInteractions(clientIpResolver);
    }

    @Test
    void record_withoutAuthenticatedAdmin_failsAndWritesNothing() {
        assertThrows(IllegalStateException.class, () -> auditLogService.record(
                AdminAuditAction.USER_RESTORE, AuditTargetType.USER, "target-1", null, null));

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_mapsRowsWithAdminUsername() {
        UUID adminId = UUID.randomUUID();
        AdminAuditLogEntity row = AdminAuditLogEntity.builder()
                .id(UUID.randomUUID())
                .adminId(adminId)
                .action(AdminAuditAction.USER_SOFT_DELETE)
                .targetType(AuditTargetType.USER)
                .targetId("target-1")
                .createdAt(Instant.parse("2026-11-07T01:00:00Z"))
                .build();
        UserEntity admin = new UserEntity();
        admin.setId(adminId);
        admin.setUsername("admin");
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
        when(userRepository.findAllById(any())).thenReturn(List.of(admin));

        PaginatedResponse<AdminAuditLogDTO> result = auditLogService.search(
                new AuditLogSearchRequest(null, null, null, null, null, null), pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("admin", result.getContent().get(0).adminUsername());
        assertEquals(AdminAuditAction.USER_SOFT_DELETE, result.getContent().get(0).action());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void search_rejectsInvalidFilters() {
        Pageable pageable = PageRequest.of(0, 20);

        assertBadRequest(new AuditLogSearchRequest("not-a-uuid", null, null, null, null, null), pageable);
        assertBadRequest(new AuditLogSearchRequest(null, "NOPE", null, null, null, null), pageable);
        assertBadRequest(new AuditLogSearchRequest(null, null, "NOPE", null, null, null), pageable);
        assertBadRequest(new AuditLogSearchRequest(null, null, null, null,
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7)), pageable);

        verifyNoInteractions(auditLogRepository);
    }

    private void assertBadRequest(AuditLogSearchRequest request, Pageable pageable) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> auditLogService.search(request, pageable));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void metadata_skipsNullValues() {
        Map<String, Object> result = AuditLogService.metadata("a", 1, "b", null, "c", "x");

        assertEquals(Map.of("a", 1, "c", "x"), result);
    }
}
