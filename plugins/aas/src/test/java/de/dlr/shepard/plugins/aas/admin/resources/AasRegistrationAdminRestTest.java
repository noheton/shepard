package de.dlr.shepard.plugins.aas.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.plugins.aas.entities.AasRegistration;
import de.dlr.shepard.plugins.aas.entities.AasRegistration.Status;
import de.dlr.shepard.plugins.aas.services.AasRegistryOutboxService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.aas.admin.io.AasRegistrationIO;
import de.dlr.shepard.plugins.aas.admin.io.AasSyncResultIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AasRegistrationAdminRestTest {

  static final String REGISTRY_URL = "https://registry.example.org";
  static final String SHELL_APP_ID = "col-aaa-111";

  AasRegistrationDAO registrationDAO;
  AasRegistryOutboxService outboxService;
  AasRegistrationAdminRest rest;

  @BeforeEach
  void setUp() {
    registrationDAO = mock(AasRegistrationDAO.class);
    outboxService = mock(AasRegistryOutboxService.class);
    rest = new AasRegistrationAdminRest();
    rest.registrationDAO = registrationDAO;
    rest.outboxService = outboxService;
  }

  // --- annotation gates ---

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AasRegistrationAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void listPathIsV2AdminAasRegistrations() {
    Path p = AasRegistrationAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/aas/registrations", p.value());
  }

  // --- GET /v2/admin/aas/registrations ---

  @Test
  void listRegistrationsReturnsEmptyPagedEnvelopeWhenNoRows() {
    when(registrationDAO.countAll()).thenReturn(0L);
    when(registrationDAO.listAll(0, 50)).thenReturn(List.of());

    Response r = rest.listRegistrations(0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<AasRegistrationIO> body = (PagedResponseIO<AasRegistrationIO>) r.getEntity();
    assertEquals(0L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertEquals(0, body.items().size());
  }

  @Test
  void listRegistrationsReturnsPagedEnvelopeWithMappedRow() {
    AasRegistration reg = buildReg(SHELL_APP_ID, Status.SYNCED, null);
    when(registrationDAO.countAll()).thenReturn(1L);
    when(registrationDAO.listAll(0, 50)).thenReturn(List.of(reg));

    Response r = rest.listRegistrations(0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<AasRegistrationIO> body = (PagedResponseIO<AasRegistrationIO>) r.getEntity();
    assertEquals(1L, body.total());
    assertEquals(1, body.items().size());
    AasRegistrationIO io = body.items().get(0);
    assertEquals(SHELL_APP_ID, io.shellAppId());
    assertEquals(REGISTRY_URL, io.registryUrl());
    assertEquals(Status.SYNCED, io.status());
  }

  @Test
  void listRegistrationsIncludesErrorMessageOnFailedRow() {
    AasRegistration reg = buildReg(SHELL_APP_ID, Status.FAILED, "HTTP 503: Service Unavailable");
    when(registrationDAO.countAll()).thenReturn(1L);
    when(registrationDAO.listAll(0, 50)).thenReturn(List.of(reg));

    Response r = rest.listRegistrations(0, 50);

    @SuppressWarnings("unchecked")
    PagedResponseIO<AasRegistrationIO> body = (PagedResponseIO<AasRegistrationIO>) r.getEntity();
    assertEquals("HTTP 503: Service Unavailable", body.items().get(0).errorMessage());
  }

  @Test
  void pageSizeIsCappedAt200() {
    when(registrationDAO.countAll()).thenReturn(0L);
    when(registrationDAO.listAll(0, AasRegistrationAdminRest.MAX_PAGE_SIZE)).thenReturn(List.of());

    Response r = rest.listRegistrations(0, 9999);

    @SuppressWarnings("unchecked")
    PagedResponseIO<AasRegistrationIO> body = (PagedResponseIO<AasRegistrationIO>) r.getEntity();
    assertEquals(AasRegistrationAdminRest.MAX_PAGE_SIZE, body.pageSize());
  }

  @Test
  void negativePageIsClampedToZero() {
    when(registrationDAO.countAll()).thenReturn(0L);
    when(registrationDAO.listAll(0, 50)).thenReturn(List.of());

    Response r = rest.listRegistrations(-5, 50);

    @SuppressWarnings("unchecked")
    PagedResponseIO<AasRegistrationIO> body = (PagedResponseIO<AasRegistrationIO>) r.getEntity();
    assertEquals(0, body.page());
  }

  // --- POST /v2/admin/aas/registrations/sync ---

  @Test
  void triggerSyncReturnsSyncedCount() {
    when(outboxService.syncAll()).thenReturn(5);

    Response r = rest.triggerSync();

    assertEquals(200, r.getStatus());
    AasSyncResultIO body = (AasSyncResultIO) r.getEntity();
    assertEquals(5, body.synced());
    verify(outboxService, times(1)).syncAll();
  }

  @Test
  void triggerSyncReturnsZeroWhenNoRegistryConfigured() {
    when(outboxService.syncAll()).thenReturn(0);

    Response r = rest.triggerSync();

    assertEquals(200, r.getStatus());
    AasSyncResultIO body = (AasSyncResultIO) r.getEntity();
    assertEquals(0, body.synced());
  }

  // --- helpers ---

  private AasRegistration buildReg(String shellAppId, Status status, String errorMessage) {
    var reg = new AasRegistration(1L);
    reg.setShellAppId(shellAppId);
    reg.setRegistryUrl(REGISTRY_URL);
    reg.setStatus(status);
    reg.setErrorMessage(errorMessage);
    reg.setCreatedAt(System.currentTimeMillis());
    reg.setUpdatedAt(System.currentTimeMillis());
    return reg;
  }
}
