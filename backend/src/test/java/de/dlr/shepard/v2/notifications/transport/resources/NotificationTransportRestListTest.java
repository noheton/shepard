package de.dlr.shepard.v2.notifications.transport.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportReadIO;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-LIST + APISIMP-NOTIF-TRANSPORT-BARE-LIST — unit tests for the GET endpoint.
 *
 * <p>Covers: annotation gates (path, role-allowed); empty result;
 * populated result wrapped in {@link PagedResponseIO}; secret fields
 * never appear in the serialised response body (defence in depth — the
 * {@code NotificationTransportReadIOTest} class proves the projection
 * shape, this class proves the full GET round-trip).
 */
class NotificationTransportRestListTest {

  private NotificationTransportService service;
  private NotificationTransportRest rest;

  @BeforeEach
  void setUp() {
    service = mock(NotificationTransportService.class);
    rest = new NotificationTransportRest();
    rest.service = service;
  }

  // ─── annotation gates ──────────────────────────────────────────────────

  @Test
  void pathIsV2AdminNotificationsTransports() {
    Path p = NotificationTransportRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/notifications/transports", p.value());
  }

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = NotificationTransportRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "NotificationTransportRest must be @RolesAllowed-gated");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  // ─── GET ───────────────────────────────────────────────────────────────

  @Test
  void list_emptyServiceReturnsPagedEnvelopeWithEmptyItems() {
    when(service.count()).thenReturn(0L);
    when(service.listPaged(0, 50)).thenReturn(List.of());

    Response r = rest.list(0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<NotificationTransportReadIO> out =
        (PagedResponseIO<NotificationTransportReadIO>) r.getEntity();
    assertNotNull(out);
    assertNotNull(out.items());
    assertTrue(out.items().isEmpty());
    assertEquals(0, out.total());
    assertEquals(0, out.page());
    assertEquals(50, out.pageSize());
  }

  @Test
  void list_returnsAllTransportsProjectedToReadIO() {
    NotificationTransport smtp = new NotificationTransport();
    smtp.setAppId("app-smtp");
    smtp.setKind(TransportKind.SMTP.name());
    smtp.setName("SMTP one");
    smtp.setEnabled(true);
    smtp.setSmtpHost("smtp.test");
    smtp.setSmtpPassword("leak-me-if-you-can");

    NotificationTransport matrix = new NotificationTransport();
    matrix.setAppId("app-matrix");
    matrix.setKind(TransportKind.MATRIX.name());
    matrix.setName("Matrix one");
    matrix.setMatrixHomeserver("https://m.test");
    matrix.setMatrixAccessToken("syt_leak-me-too");

    when(service.count()).thenReturn(2L);
    when(service.listPaged(0, 50)).thenReturn(List.of(smtp, matrix));

    Response r = rest.list(0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<NotificationTransportReadIO> out =
        (PagedResponseIO<NotificationTransportReadIO>) r.getEntity();
    assertEquals(2, out.items().size());
    assertEquals(2, out.total());
    assertEquals("app-smtp", out.items().get(0).appId());
    assertEquals("app-matrix", out.items().get(1).appId());
  }

  @Test
  void list_xTotalCountHeaderMatchesTotal() {
    when(service.count()).thenReturn(3L);
    when(service.listPaged(0, 50)).thenReturn(List.of());

    Response r = rest.list(0, 50);

    assertEquals(200, r.getStatus());
    Object header = r.getHeaders().getFirst("X-Total-Count");
    assertNotNull(header, "X-Total-Count header must be present");
    assertEquals(3L, header);
  }

  @Test
  void list_page1_usesCorrectPageParameters() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    t.setKind(TransportKind.SMTP.name());
    t.setName("T1");

    when(service.count()).thenReturn(5L);
    when(service.listPaged(1, 2)).thenReturn(List.of(t));

    Response r = rest.list(1, 2);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<NotificationTransportReadIO> out =
        (PagedResponseIO<NotificationTransportReadIO>) r.getEntity();
    assertEquals(5, out.total());
    assertEquals(1, out.page());
    assertEquals(2, out.pageSize());
    assertEquals(1, out.items().size());
  }

  @Test
  void list_serializedJson_omitsCredentialValuesAndNames() throws Exception {
    NotificationTransport smtp = new NotificationTransport();
    smtp.setAppId("app-smtp");
    smtp.setKind(TransportKind.SMTP.name());
    smtp.setName("SMTP");
    smtp.setSmtpHost("smtp.test");
    smtp.setSmtpPassword("plaintext-password-do-not-leak");

    NotificationTransport matrix = new NotificationTransport();
    matrix.setAppId("app-matrix");
    matrix.setKind(TransportKind.MATRIX.name());
    matrix.setName("Matrix");
    matrix.setMatrixHomeserver("https://m.test");
    matrix.setMatrixAccessToken("syt_access-token-do-not-leak");

    when(service.count()).thenReturn(2L);
    when(service.listPaged(0, 50)).thenReturn(List.of(smtp, matrix));

    Response r = rest.list(0, 50);
    // Serialize the PagedResponseIO envelope — credential values must not appear anywhere.
    String json = new ObjectMapper().writeValueAsString(r.getEntity());

    assertFalse(json.contains("plaintext-password-do-not-leak"),
        "GET response JSON leaks SMTP password: " + json);
    assertFalse(json.contains("syt_access-token-do-not-leak"),
        "GET response JSON leaks Matrix access token: " + json);
    assertFalse(json.toLowerCase().contains("password"),
        "GET response JSON contains 'password' key/value: " + json);
    assertFalse(json.toLowerCase().contains("accesstoken"),
        "GET response JSON contains 'accessToken' key/value: " + json);
  }
}
