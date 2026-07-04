package de.dlr.shepard.v2.notifications.transport.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — unit tests for the entity contract.
 *
 * <p>Covers: identity (appId-based equals/hashCode), kind/field round-trip,
 * default field state, write-only fields are present on the entity (the
 * IO-side omission is tested in {@code NotificationTransportReadIOTest}).
 */
class NotificationTransportTest {

  @Test
  void defaultsAreSafeOnNoArgsConstruction() {
    NotificationTransport t = new NotificationTransport();
    assertNull(t.getAppId());
    assertNull(t.getKind());
    assertNull(t.getName());
    assertFalse(t.isEnabled(), "enabled defaults to false (opt-in)");
    assertNull(t.getLastTestResult());
    assertNull(t.getLastTestedAt());
    assertNull(t.getSmtpHost());
    assertNull(t.getSmtpPort());
    assertNull(t.getSmtpPassword());
    assertNull(t.getMatrixHomeserver());
    assertNull(t.getMatrixAccessToken());
  }

  @Test
  void smtpFieldsRoundTrip() {
    NotificationTransport t = new NotificationTransport();
    t.setKind(TransportKind.SMTP.name());
    t.setName("primary SMTP");
    t.setEnabled(true);
    t.setSmtpHost("smtp.example.org");
    t.setSmtpPort(587);
    t.setSmtpUsername("noreply");
    t.setSmtpPassword("hunter2");
    t.setSmtpFrom("noreply@example.org");
    t.setSmtpTls(true);

    assertEquals("SMTP", t.getKind());
    assertEquals("primary SMTP", t.getName());
    assertTrue(t.isEnabled());
    assertEquals("smtp.example.org", t.getSmtpHost());
    assertEquals(587, t.getSmtpPort());
    assertEquals("noreply", t.getSmtpUsername());
    assertEquals("hunter2", t.getSmtpPassword(),
        "entity stores the password — read-side IO omits it");
    assertEquals("noreply@example.org", t.getSmtpFrom());
    assertTrue(t.getSmtpTls());
  }

  @Test
  void matrixFieldsRoundTrip() {
    NotificationTransport t = new NotificationTransport();
    t.setKind(TransportKind.MATRIX.name());
    t.setName("DLR Matrix");
    t.setMatrixHomeserver("https://matrix.dlr.de");
    t.setMatrixAccessToken("syt_abc123");
    t.setMatrixDefaultRoom("!ops:matrix.dlr.de");

    assertEquals("MATRIX", t.getKind());
    assertEquals("https://matrix.dlr.de", t.getMatrixHomeserver());
    assertEquals("syt_abc123", t.getMatrixAccessToken(),
        "entity stores the token — read-side IO omits it");
    assertEquals("!ops:matrix.dlr.de", t.getMatrixDefaultRoom());
  }

  @Test
  void equalsAndHashCodeKeyOnAppId() {
    NotificationTransport a = new NotificationTransport();
    a.setAppId("app-1");
    a.setName("a");
    NotificationTransport b = new NotificationTransport();
    b.setAppId("app-1");
    b.setName("b");
    NotificationTransport c = new NotificationTransport();
    c.setAppId("app-2");

    assertEquals(a, b, "equality keys on appId only");
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
    assertNotNull(a.getUniqueId());
  }
}
