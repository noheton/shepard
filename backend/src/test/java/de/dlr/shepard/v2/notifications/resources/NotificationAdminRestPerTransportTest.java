package de.dlr.shepard.v2.notifications.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.notifications.io.TestNotificationIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportRegistry;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationSender;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NTF1-BACKEND-TEST-PER-TRANSPORT — tests for the {@code transportId}
 * branch of {@code POST /v2/admin/notifications/test}.
 *
 * <p>Covers: unknown appId → 404; no registered sender → 503;
 * sender returns true → 200; sender returns false → 502; in-app
 * legacy path still works when transportId is null; last-test
 * outcome recorded on the transport row.
 */
class NotificationAdminRestPerTransportTest {

  private NotificationService notifService;
  private NotificationTransportService transportService;
  private NotificationTransportRegistry registry;
  private NotificationAdminRest rest;
  private SecurityContext sc;

  @BeforeEach
  void setUp() {
    notifService = mock(NotificationService.class);
    transportService = mock(NotificationTransportService.class);
    registry = mock(NotificationTransportRegistry.class);
    rest = new NotificationAdminRest();
    rest.service = notifService;
    rest.transportService = transportService;
    rest.transportRegistry = registry;

    sc = mock(SecurityContext.class);
    Principal p = mock(Principal.class);
    when(p.getName()).thenReturn("admin");
    when(sc.getUserPrincipal()).thenReturn(p);
  }

  // ─── legacy (transportId absent) ────────────────────────────────────────

  @Test
  void sendTest_transportIdAbsent_keepsLegacyInAppPath() {
    TestNotificationIO body = new TestNotificationIO();
    body.setTitle("hi");
    body.setBody("hello");
    // transportId left null

    when(notifService.publish(any(), any(), any(), any(), any(), any(), any()))
      .thenAnswer(inv -> {
        de.dlr.shepard.v2.notifications.entities.Notification n =
            new de.dlr.shepard.v2.notifications.entities.Notification();
        n.setAppId("notif-1");
        n.setTitle(inv.getArgument(4));
        n.setBody(inv.getArgument(5));
        return n;
      });

    Response r = rest.sendTest(body, sc);

    assertEquals(201, r.getStatus());
    verify(transportService, never()).findByAppId(any());
    verify(registry, never()).resolve(any());
  }

  @Test
  void sendTest_transportIdBlank_keepsLegacyInAppPath() {
    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("   ");
    body.setTitle("hi");

    when(notifService.publish(any(), any(), any(), any(), any(), any(), any()))
      .thenAnswer(inv -> {
        de.dlr.shepard.v2.notifications.entities.Notification n =
            new de.dlr.shepard.v2.notifications.entities.Notification();
        n.setAppId("notif-1");
        return n;
      });

    Response r = rest.sendTest(body, sc);
    assertEquals(201, r.getStatus());
    verify(transportService, never()).findByAppId(any());
  }

  // ─── transportId branch ────────────────────────────────────────────────

  @Test
  void sendTest_unknownTransportId_returns404() {
    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("missing");
    body.setTitle("hi");

    when(transportService.findByAppId("missing")).thenReturn(Optional.empty());

    Response r = rest.sendTest(body, sc);
    assertEquals(404, r.getStatus());
    verify(registry, never()).resolve(any());
  }

  @Test
  void sendTest_noRegisteredSender_returns503() {
    NotificationTransport t = matrixTransport();
    when(transportService.findByAppId("app-mx")).thenReturn(Optional.of(t));
    when(registry.resolve(t)).thenReturn(Optional.empty());

    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("app-mx");

    Response r = rest.sendTest(body, sc);

    assertEquals(503, r.getStatus());
    assertNotNull(r.getEntity());
    // Last test outcome FAIL persisted.
    ArgumentCaptor<NotificationTransport> cap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(transportService, times(1)).save(cap.capture());
    assertEquals("FAIL", cap.getValue().getLastTestResult());
    assertNotNull(cap.getValue().getLastTestedAt());
  }

  @Test
  void sendTest_senderReturnsTrue_returns200AndRecordsOk() {
    NotificationTransport t = smtpTransport();
    when(transportService.findByAppId("app-smtp")).thenReturn(Optional.of(t));
    NotificationSender sender = mock(NotificationSender.class);
    when(sender.send(eq(t), any(NotificationMessage.class))).thenReturn(true);
    when(registry.resolve(t)).thenReturn(Optional.of(sender));

    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("app-smtp");
    body.setRecipientAddress("alice@example.org");
    body.setTitle("Alert");
    body.setBody("test body");

    Response r = rest.sendTest(body, sc);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<NotificationMessage> msgCap = ArgumentCaptor.forClass(NotificationMessage.class);
    verify(sender).send(eq(t), msgCap.capture());
    assertEquals("alice@example.org", msgCap.getValue().recipient());
    assertEquals("Alert", msgCap.getValue().title());

    ArgumentCaptor<NotificationTransport> savedCap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(transportService).save(savedCap.capture());
    assertEquals("OK", savedCap.getValue().getLastTestResult());
    assertEquals("delivered", savedCap.getValue().getLastTestDetail());
  }

  @Test
  void sendTest_senderReturnsFalse_returns502AndRecordsFail() {
    NotificationTransport t = smtpTransport();
    when(transportService.findByAppId("app-smtp")).thenReturn(Optional.of(t));
    NotificationSender sender = mock(NotificationSender.class);
    when(sender.send(eq(t), any(NotificationMessage.class))).thenReturn(false);
    when(registry.resolve(t)).thenReturn(Optional.of(sender));

    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("app-smtp");
    body.setTitle("hi");

    Response r = rest.sendTest(body, sc);

    assertEquals(502, r.getStatus());
    ArgumentCaptor<NotificationTransport> savedCap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(transportService).save(savedCap.capture());
    assertEquals("FAIL", savedCap.getValue().getLastTestResult());
  }

  @Test
  void sendTest_senderThrows_returns502AndRecordsFail() {
    NotificationTransport t = smtpTransport();
    when(transportService.findByAppId("app-smtp")).thenReturn(Optional.of(t));
    NotificationSender sender = mock(NotificationSender.class);
    when(sender.send(eq(t), any(NotificationMessage.class)))
      .thenThrow(new RuntimeException("boom"));
    when(registry.resolve(t)).thenReturn(Optional.of(sender));

    TestNotificationIO body = new TestNotificationIO();
    body.setTransportId("app-smtp");
    body.setTitle("hi");

    Response r = rest.sendTest(body, sc);

    assertEquals(502, r.getStatus());
    ProblemJson entity = (ProblemJson) r.getEntity();
    assertTrue(entity.detail().contains("boom"), "error message included in response body");
    ArgumentCaptor<NotificationTransport> savedCap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(transportService).save(savedCap.capture());
    assertEquals("FAIL", savedCap.getValue().getLastTestResult());
  }

  // ─── helpers ───────────────────────────────────────────────────────────

  private static NotificationTransport smtpTransport() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-smtp");
    t.setKind(TransportKind.SMTP.name());
    t.setName("primary SMTP");
    t.setSmtpHost("smtp.test");
    t.setSmtpFrom("noreply@test");
    return t;
  }

  private static NotificationTransport matrixTransport() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-mx");
    t.setKind(TransportKind.MATRIX.name());
    t.setName("matrix");
    t.setMatrixHomeserver("https://m.test");
    t.setMatrixAccessToken("tk");
    t.setMatrixDefaultRoom("!r:hs");
    return t;
  }
}
