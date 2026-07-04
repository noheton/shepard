package de.dlr.shepard.v2.notifications.transport.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportReadIO;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportWriteIO;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NTF1-BACKEND-CRUD — POST / PATCH / DELETE endpoint tests.
 *
 * <p>Covers happy-path create, validation rejections (missing kind,
 * invalid kind, missing name), happy-path patch (touched-fields only),
 * 404 paths for unknown appId, and the helper {@code isValidKind}.
 */
class NotificationTransportRestCrudTest {

  private NotificationTransportService service;
  private NotificationTransportRest rest;

  @BeforeEach
  void setUp() {
    service = mock(NotificationTransportService.class);
    rest = new NotificationTransportRest();
    rest.service = service;
  }

  // ─── POST ──────────────────────────────────────────────────────────────

  @Test
  void create_happyPath_returns201AndProjectsToReadIO() {
    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setKind(TransportKind.SMTP.name());
    body.setName("primary SMTP");
    body.setEnabled(true);
    body.setSmtpHost("smtp.test");
    body.setSmtpPort(587);
    body.setSmtpUsername("noreply");
    body.setSmtpPassword("secret");
    body.setSmtpFrom("noreply@test");
    body.setSmtpTls(true);

    when(service.save(any(NotificationTransport.class))).thenAnswer(inv -> {
      NotificationTransport t = inv.getArgument(0);
      t.setAppId("minted-app-id");
      return t;
    });

    Response r = rest.create(body);

    assertEquals(201, r.getStatus());
    NotificationTransportReadIO out = (NotificationTransportReadIO) r.getEntity();
    assertEquals("minted-app-id", out.appId());
    assertEquals("SMTP", out.kind());
    assertEquals("primary SMTP", out.name());
    assertTrue(out.enabled());

    ArgumentCaptor<NotificationTransport> cap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(service, times(1)).save(cap.capture());
    NotificationTransport saved = cap.getValue();
    assertEquals("secret", saved.getSmtpPassword(),
        "password persists on the entity (write side)");
  }

  @Test
  void create_missingKind_returns400Problem() {
    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setName("noKind");

    Response r = rest.create(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson p = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(NotificationTransportRest.PROBLEM_TYPE_MISSING_FIELD, p.type());
    verify(service, never()).save(any());
  }

  @Test
  void create_invalidKind_returns400Problem() {
    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setKind("TELEPATHIC");
    body.setName("invalid");

    Response r = rest.create(body);

    assertEquals(400, r.getStatus());
    ProblemJson p = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(NotificationTransportRest.PROBLEM_TYPE_INVALID_KIND, p.type());
    verify(service, never()).save(any());
  }

  @Test
  void create_missingName_returns400Problem() {
    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setKind(TransportKind.MATRIX.name());

    Response r = rest.create(body);

    assertEquals(400, r.getStatus());
    ProblemJson p = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(NotificationTransportRest.PROBLEM_TYPE_MISSING_FIELD, p.type());
    verify(service, never()).save(any());
  }

  @Test
  void create_nullBody_returns400Problem() {
    Response r = rest.create(null);
    assertEquals(400, r.getStatus());
  }

  // ─── PATCH ─────────────────────────────────────────────────────────────

  @Test
  void patch_unknownAppId_returns404() {
    when(service.findByAppId("missing")).thenReturn(Optional.empty());

    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setName("renamed");

    Response r = rest.patch("missing", body);

    assertEquals(404, r.getStatus());
    verify(service, never()).save(any());
  }

  @Test
  void patch_touchedFieldsOnly_updatesAndPreservesOthers() {
    NotificationTransport current = new NotificationTransport();
    current.setAppId("app-1");
    current.setKind(TransportKind.SMTP.name());
    current.setName("old name");
    current.setEnabled(true);
    current.setSmtpHost("old.host");
    current.setSmtpPassword("old-password");
    when(service.findByAppId("app-1")).thenReturn(Optional.of(current));
    when(service.save(any(NotificationTransport.class))).thenAnswer(inv -> inv.getArgument(0));

    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setName("new name");
    // smtpHost untouched; smtpPassword untouched

    Response r = rest.patch("app-1", body);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<NotificationTransport> cap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(service).save(cap.capture());
    NotificationTransport saved = cap.getValue();
    assertEquals("new name", saved.getName(), "name updated");
    assertEquals("old.host", saved.getSmtpHost(), "smtpHost preserved (untouched)");
    assertEquals("old-password", saved.getSmtpPassword(), "smtpPassword preserved (untouched)");
    assertEquals(TransportKind.SMTP.name(), saved.getKind(), "kind preserved");
    assertTrue(saved.isEnabled(), "enabled preserved");
  }

  @Test
  void patch_explicitNullOnSmtpPassword_clearsField() {
    NotificationTransport current = new NotificationTransport();
    current.setAppId("app-1");
    current.setKind(TransportKind.SMTP.name());
    current.setName("smtp");
    current.setSmtpPassword("old-password");
    when(service.findByAppId("app-1")).thenReturn(Optional.of(current));
    when(service.save(any(NotificationTransport.class))).thenAnswer(inv -> inv.getArgument(0));

    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setSmtpPassword(null); // explicit null → clear

    Response r = rest.patch("app-1", body);

    assertEquals(200, r.getStatus());
    ArgumentCaptor<NotificationTransport> cap = ArgumentCaptor.forClass(NotificationTransport.class);
    verify(service).save(cap.capture());
    assertNull(cap.getValue().getSmtpPassword(), "explicit null clears smtpPassword");
  }

  @Test
  void patch_invalidKind_returns400Problem() {
    NotificationTransport current = new NotificationTransport();
    current.setAppId("app-1");
    current.setKind(TransportKind.SMTP.name());
    when(service.findByAppId("app-1")).thenReturn(Optional.of(current));

    NotificationTransportWriteIO body = new NotificationTransportWriteIO();
    body.setKind("BOGUS");

    Response r = rest.patch("app-1", body);

    assertEquals(400, r.getStatus());
    ProblemJson p = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(NotificationTransportRest.PROBLEM_TYPE_INVALID_KIND, p.type());
    verify(service, never()).save(any());
  }

  @Test
  void patch_responseOmitsCredentials() throws Exception {
    NotificationTransport current = new NotificationTransport();
    current.setAppId("app-1");
    current.setKind(TransportKind.SMTP.name());
    current.setName("smtp");
    current.setSmtpPassword("leak-me-not");
    when(service.findByAppId("app-1")).thenReturn(Optional.of(current));
    when(service.save(any(NotificationTransport.class))).thenAnswer(inv -> inv.getArgument(0));

    Response r = rest.patch("app-1", new NotificationTransportWriteIO());

    assertEquals(200, r.getStatus());
    String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(r.getEntity());
    assertTrue(!json.contains("leak-me-not"), "patch response leaked password: " + json);
  }

  // ─── DELETE ────────────────────────────────────────────────────────────

  @Test
  void delete_happyPath_returns204() {
    when(service.deleteByAppId("app-1")).thenReturn(true);

    Response r = rest.delete("app-1");

    assertEquals(204, r.getStatus());
    assertNull(r.getEntity());
  }

  @Test
  void delete_unknownAppId_returns404() {
    when(service.deleteByAppId("missing")).thenReturn(false);

    Response r = rest.delete("missing");

    assertEquals(404, r.getStatus());
  }

  // ─── helpers ───────────────────────────────────────────────────────────

  @Test
  void isValidKind_acceptsKnownEnumValues() {
    assertTrue(NotificationTransportRest.isValidKind("SMTP"));
    assertTrue(NotificationTransportRest.isValidKind("MATRIX"));
    assertTrue(NotificationTransportRest.isValidKind("INAPP"));
  }

  @Test
  void isValidKind_rejectsUnknownOrNull() {
    assertTrue(!NotificationTransportRest.isValidKind(null));
    assertTrue(!NotificationTransportRest.isValidKind(""));
    assertTrue(!NotificationTransportRest.isValidKind("TELEPATHIC"));
    assertTrue(!NotificationTransportRest.isValidKind("smtp"),
        "case-sensitive — operator must use enum's canonical form");
  }
}
