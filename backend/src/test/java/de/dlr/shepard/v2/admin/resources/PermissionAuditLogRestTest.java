package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
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
 * F3 — unit tests for the {@code GET /v2/admin/permission-audit/log} endpoint.
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
    // The endpoint class carries @RolesAllowed so the Quarkus security layer
    // can enforce it automatically in addition to the manual check.
    RolesAllowed gate = InstanceAdminRest.class.getAnnotation(RolesAllowed.class);
    // InstanceAdminRest is not class-level annotated, but each method is —
    // verify the log endpoint method carries the annotation.
    var methods = java.util.Arrays.stream(InstanceAdminRest.class.getMethods())
      .filter(m -> m.getName().equals("permissionAuditLog"))
      .toList();
    assertNotNull(methods, "permissionAuditLog method must exist");
    assertTrue(!methods.isEmpty(), "permissionAuditLog method must exist");
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
  void noFilters_returnsAllRows() {
    var row1 = new PermissionAuditLogEntryIO("pal-uuid-1", "2026-01-01T00:00:00Z", "app-1", "Collection", "alice", "GRANT", null);
    var row2 = new PermissionAuditLogEntryIO("pal-uuid-2", "2026-01-02T00:00:00Z", "app-2", "DataObject", "bob", "UPDATE", "{}");
    when(permissionAuditLogQueryService.query(isNull(), isNull(), isNull(), isNull(), eq(0), eq(50)))
      .thenReturn(List.of(row1, row2));

    Response r = resource.permissionAuditLog(adminCtx, null, null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<PermissionAuditLogEntryIO> body = (List<PermissionAuditLogEntryIO>) r.getEntity();
    assertEquals(2, body.size());
    assertEquals("app-1", body.get(0).getEntityAppId());
    assertEquals("GRANT", body.get(0).getAction());
  }

  @Test
  void entityAppIdFilter_passedThrough() {
    when(permissionAuditLogQueryService.query(eq("my-app-id"), isNull(), isNull(), isNull(), eq(0), eq(50)))
      .thenReturn(List.of(
        new PermissionAuditLogEntryIO("pal-uuid-5", "2026-03-01T10:00:00Z", "my-app-id", "Collection", "carol", "REVOKE", null)
      ));

    Response r = resource.permissionAuditLog(adminCtx, "my-app-id", null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<PermissionAuditLogEntryIO> body = (List<PermissionAuditLogEntryIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("my-app-id", body.get(0).getEntityAppId());
    assertEquals("REVOKE", body.get(0).getAction());

    verify(permissionAuditLogQueryService).query(eq("my-app-id"), isNull(), isNull(), isNull(), eq(0), eq(50));
  }

  @Test
  void validIso8601DateRange_parsedAndPassedThrough() {
    String from = "2026-01-01T00:00:00Z";
    String to = "2026-02-01T00:00:00Z";
    Instant fromI = Instant.parse(from);
    Instant toI = Instant.parse(to);

    when(permissionAuditLogQueryService.query(isNull(), isNull(), eq(fromI), eq(toI), eq(0), eq(20)))
      .thenReturn(List.of());

    Response r = resource.permissionAuditLog(adminCtx, null, null, from, to, 0, 20);

    assertEquals(200, r.getStatus());
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
  void emptyResultSet_returns200WithEmptyList() {
    when(permissionAuditLogQueryService.query(any(), any(), any(), any(), eq(0), eq(50)))
      .thenReturn(List.of());

    Response r = resource.permissionAuditLog(adminCtx, null, null, null, null, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<PermissionAuditLogEntryIO> body = (List<PermissionAuditLogEntryIO>) r.getEntity();
    assertTrue(body.isEmpty());
  }

  // ─── APISIMP-INSTANCE-ADMIN-AUDIT-PARAMS regression ──────────────────────

  private static String auditLogParamDesc(String queryParamName) throws NoSuchMethodException {
    Method method = InstanceAdminRest.class.getMethod(
        "permissionAuditLog",
        SecurityContext.class, String.class, String.class,
        String.class, String.class, int.class, int.class);
    return Arrays.stream(method.getParameters())
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
    String desc = auditLogParamDesc("entityAppId");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'entityAppId' must carry a @Parameter description");
  }

  @Test
  void auditLog_actorParamHasParameterAnnotation() throws NoSuchMethodException {
    String desc = auditLogParamDesc("actor");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'actor' must carry a @Parameter description");
  }

  @Test
  void auditLog_fromParamDescriptionMentions400() throws NoSuchMethodException {
    String desc = auditLogParamDesc("from");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'from' must carry a @Parameter description");
    Assertions.assertTrue(desc.contains("400"),
        "permissionAuditLog() 'from' description must mention 400 (parse-error behaviour)");
  }

  @Test
  void auditLog_toParamDescriptionMentions400() throws NoSuchMethodException {
    String desc = auditLogParamDesc("to");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'to' must carry a @Parameter description");
    Assertions.assertTrue(desc.contains("400"),
        "permissionAuditLog() 'to' description must mention 400 (parse-error behaviour)");
  }

  @Test
  void auditLog_pageParamHasParameterAnnotation() throws NoSuchMethodException {
    String desc = auditLogParamDesc("page");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'page' must carry a @Parameter description");
  }

  @Test
  void auditLog_pageSizeParamDescriptionMentions500() throws NoSuchMethodException {
    String desc = auditLogParamDesc("pageSize");
    Assertions.assertFalse(desc.isBlank(),
        "permissionAuditLog() 'pageSize' must carry a @Parameter description");
    Assertions.assertTrue(desc.contains("500"),
        "permissionAuditLog() 'pageSize' description must mention the 500 server-side cap");
  }
}
