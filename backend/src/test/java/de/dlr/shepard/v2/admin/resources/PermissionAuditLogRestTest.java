package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.PermissionAuditLogEntryIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * F3 / APISIMP-PERMISSION-AUDIT-LOG-PAGINATION — unit tests for
 * {@code GET /v2/admin/permission-audit/log}.
 */
class PermissionAuditLogRestTest {

  @Mock
  InstanceAdminService instanceAdminService;

  @Mock
  PermissionAuditService permissionAuditService;

  @Mock
  PermissionAuditLogQueryService permissionAuditLogQueryService;

  @Mock
  AuthenticationContext authenticationContext;

  @InjectMocks
  InstanceAdminRest resource;

  private SecurityContext adminCtx;
  private SecurityContext nonAdminCtx;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    adminCtx = mock(SecurityContext.class);
    when(adminCtx.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    nonAdminCtx = mock(SecurityContext.class);
    when(nonAdminCtx.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
  }

  // ─── role gate ─────────────────────────────────────────────────────────────

  @Test
  void classIsAnnotatedWithInstanceAdminRole() {
    var methods = java.util.Arrays.stream(InstanceAdminRest.class.getMethods())
      .filter(m -> m.getName().equals("permissionAuditLog"))
      .toList();
    assertNotNull(methods, "permissionAuditLog method must exist");
    assertFalse(methods.isEmpty(), "permissionAuditLog method must exist");
    RolesAllowed methodGate = methods.get(0).getAnnotation(RolesAllowed.class);
    assertNotNull(methodGate, "permissionAuditLog must be @RolesAllowed-gated");
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, methodGate.value()[0]);
  }

  @Test
  void nonAdmin_isRejected() {
    assertThrows(
      de.dlr.shepard.common.exceptions.InvalidAuthException.class,
      () -> resource.permissionAuditLog(nonAdminCtx, null, null, null, null, 0, 50)
    );
  }

  // ─── happy path ────────────────────────────────────────────────────────────

  @Test
  void noFilters_returnsPagedResponse() {
    var row1 = new PermissionAuditLogEntryIO("pal-uuid-1", "2026-01-01T00:00:00Z", "app-1", "Collection", "alice", "GRANT", null);
    var row2 = new PermissionAuditLogEntryIO("pal-uuid-2", "2026-01-02T00:00:00Z", "app-2", "DataObject", "bob", "UPDATE", "{}");
    when(permissionAuditLogQueryService.count(isNull(), isNull(), isNull(), isNull())).thenReturn(2L);
    when(permissionAuditLogQueryService.query(isNull(), isNull(), isNull(), isNull(), eq(0), eq(50)))
      .thenReturn(List.of(row1, row2));

    Response r = resource.permissionAuditLog(adminCtx, null, null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PermissionAuditLogEntryIO> body = (PagedResponseIO<PermissionAuditLogEntryIO>) r.getEntity();
    assertEquals(2, body.items().size());
    assertEquals(2L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertEquals("app-1", body.items().get(0).getEntityAppId());
    assertEquals("GRANT", body.items().get(0).getAction());
  }

  @Test
  void xTotalCountHeader_isSet() {
    when(permissionAuditLogQueryService.count(isNull(), isNull(), isNull(), isNull())).thenReturn(7L);
    when(permissionAuditLogQueryService.query(any(), any(), any(), any(), eq(0), eq(50)))
      .thenReturn(List.of());

    Response r = resource.permissionAuditLog(adminCtx, null, null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    assertEquals("7", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void entityAppIdFilter_passedThrough() {
    when(permissionAuditLogQueryService.count(eq("my-app-id"), isNull(), isNull(), isNull())).thenReturn(1L);
    when(permissionAuditLogQueryService.query(eq("my-app-id"), isNull(), isNull(), isNull(), eq(0), eq(50)))
      .thenReturn(List.of(
        new PermissionAuditLogEntryIO("pal-uuid-5", "2026-03-01T10:00:00Z", "my-app-id", "Collection", "carol", "REVOKE", null)
      ));

    Response r = resource.permissionAuditLog(adminCtx, "my-app-id", null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PermissionAuditLogEntryIO> body = (PagedResponseIO<PermissionAuditLogEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("my-app-id", body.items().get(0).getEntityAppId());
    assertEquals("REVOKE", body.items().get(0).getAction());

    verify(permissionAuditLogQueryService).count(eq("my-app-id"), isNull(), isNull(), isNull());
    verify(permissionAuditLogQueryService).query(eq("my-app-id"), isNull(), isNull(), isNull(), eq(0), eq(50));
  }

  @Test
  void validIso8601DateRange_parsedAndPassedThrough() {
    String from = "2026-01-01T00:00:00Z";
    String to = "2026-02-01T00:00:00Z";
    Instant fromI = Instant.parse(from);
    Instant toI = Instant.parse(to);

    when(permissionAuditLogQueryService.count(isNull(), isNull(), eq(fromI), eq(toI))).thenReturn(0L);
    when(permissionAuditLogQueryService.query(isNull(), isNull(), eq(fromI), eq(toI), eq(0), eq(20)))
      .thenReturn(List.of());

    Response r = resource.permissionAuditLog(adminCtx, null, null, from, to, 0, 20);

    assertEquals(200, r.getStatus());
    verify(permissionAuditLogQueryService).count(isNull(), isNull(), eq(fromI), eq(toI));
    verify(permissionAuditLogQueryService).query(isNull(), isNull(), eq(fromI), eq(toI), eq(0), eq(20));
  }

  @Test
  void invalidFromDate_returns400() {
    Response r = resource.permissionAuditLog(adminCtx, null, null, "not-a-date", null, 0, 50);
    assertEquals(400, r.getStatus());
  }

  @Test
  void invalidToDate_returns400() {
    Response r = resource.permissionAuditLog(adminCtx, null, null, null, "2026-13-99", 0, 50);
    assertEquals(400, r.getStatus());
  }

  @Test
  void emptyResultSet_returns200WithEmptyItems() {
    when(permissionAuditLogQueryService.count(any(), any(), any(), any())).thenReturn(0L);
    when(permissionAuditLogQueryService.query(any(), any(), any(), any(), eq(0), eq(50)))
      .thenReturn(List.of());

    Response r = resource.permissionAuditLog(adminCtx, null, null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PermissionAuditLogEntryIO> body = (PagedResponseIO<PermissionAuditLogEntryIO>) r.getEntity();
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
  }

  // ─── parameter annotation regression ──────────────────────────────────────

  private static Method auditLogMethod() throws NoSuchMethodException {
    return InstanceAdminRest.class.getMethod(
        "permissionAuditLog",
        SecurityContext.class, String.class, String.class,
        String.class, String.class, int.class, int.class);
  }

  private static String auditLogParamDesc(String queryParamName) throws NoSuchMethodException {
    return Arrays.stream(auditLogMethod().getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && queryParamName.equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          var ann = p.getAnnotation(
              org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst().orElse("");
  }

  @Test
  void auditLog_entityAppIdParamHasParameterAnnotation() throws NoSuchMethodException {
    assertFalse(auditLogParamDesc("entityAppId").isBlank(),
        "permissionAuditLog() 'entityAppId' must carry a @Parameter description");
  }

  @Test
  void auditLog_actorParamHasParameterAnnotation() throws NoSuchMethodException {
    assertFalse(auditLogParamDesc("actor").isBlank(),
        "permissionAuditLog() 'actor' must carry a @Parameter description");
  }

  @Test
  void auditLog_fromParamDescriptionMentions400() throws NoSuchMethodException {
    String desc = auditLogParamDesc("from");
    assertFalse(desc.isBlank(),
        "permissionAuditLog() 'from' must carry a @Parameter description");
    assertTrue(desc.contains("400"),
        "permissionAuditLog() 'from' description must mention 400 (parse-error behaviour)");
  }

  @Test
  void auditLog_toParamDescriptionMentions400() throws NoSuchMethodException {
    String desc = auditLogParamDesc("to");
    assertFalse(desc.isBlank(),
        "permissionAuditLog() 'to' must carry a @Parameter description");
    assertTrue(desc.contains("400"),
        "permissionAuditLog() 'to' description must mention 400 (parse-error behaviour)");
  }

  @Test
  void auditLog_pageParamHasParameterAnnotation() throws NoSuchMethodException {
    assertFalse(auditLogParamDesc("page").isBlank(),
        "permissionAuditLog() 'page' must carry a @Parameter description");
  }

  @Test
  void auditLog_pageSizeParamDescriptionMentions200() throws NoSuchMethodException {
    String desc = auditLogParamDesc("pageSize");
    assertFalse(desc.isBlank(),
        "permissionAuditLog() 'pageSize' must carry a @Parameter description");
    assertTrue(desc.contains("200"),
        "permissionAuditLog() 'pageSize' description must mention the 200 server-side cap");
  }

  @Test
  void auditLog_pageParamHasPositiveOrZeroAnnotation() throws NoSuchMethodException {
    Parameter pageParam = Arrays.stream(auditLogMethod().getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "page".equals(p.getAnnotation(QueryParam.class).value()))
        .findFirst().orElseThrow();
    assertNotNull(pageParam.getAnnotation(PositiveOrZero.class),
        "permissionAuditLog() 'page' must carry @PositiveOrZero");
  }

  @Test
  void auditLog_pageSizeParamHasMinAndMaxAnnotations() throws NoSuchMethodException {
    Parameter pageSizeParam = Arrays.stream(auditLogMethod().getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "pageSize".equals(p.getAnnotation(QueryParam.class).value()))
        .findFirst().orElseThrow();
    Min min = pageSizeParam.getAnnotation(Min.class);
    Max max = pageSizeParam.getAnnotation(Max.class);
    assertNotNull(min, "permissionAuditLog() 'pageSize' must carry @Min");
    assertEquals(1L, min.value(), "@Min value must be 1");
    assertNotNull(max, "permissionAuditLog() 'pageSize' must carry @Max");
    assertEquals(200L, max.value(), "@Max value must be 200");
  }
}
