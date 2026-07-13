package de.dlr.shepard.v2.notifications.transport.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-LIST — LOAD-BEARING regression test for the write-only
 * credential contract.
 *
 * <p>Populates a {@link NotificationTransport} entity with secret
 * fields (smtpPassword + matrixAccessToken), projects it through
 * {@link NotificationTransportReadIO#from(NotificationTransport)}, and
 * serialises the result to JSON via Jackson. The test then asserts
 * that the secret VALUES do not appear in the JSON — and additionally
 * that the secret KEY NAMES ("password", "accessToken") are absent.
 *
 * <p>This is the only test that protects against a future refactor
 * accidentally unifying the read + write IOs and exposing credentials.
 */
class NotificationTransportReadIOTest {

  private static final String SECRET_PASSWORD = "hunter2-do-not-leak";
  private static final String SECRET_TOKEN = "syt_secret-access-token-NEVER";

  @Test
  void from_omitsSmtpPassword_inProjection() {
    NotificationTransport t = populatedSmtp();
    NotificationTransportReadIO io = NotificationTransportReadIO.from(t);

    // The record literally does not have a smtpPassword field.
    // This is a smoke check that nothing on the IO surface exposes it.
    String dump = io.toString();
    assertFalse(dump.contains(SECRET_PASSWORD),
        "IO toString() must not contain smtpPassword value");
  }

  @Test
  void from_omitsMatrixAccessToken_inProjection() {
    NotificationTransport t = populatedMatrix();
    NotificationTransportReadIO io = NotificationTransportReadIO.from(t);

    String dump = io.toString();
    assertFalse(dump.contains(SECRET_TOKEN),
        "IO toString() must not contain matrixAccessToken value");
  }

  @Test
  void jsonSerialization_omitsCredentialValues_andCredentialFieldNames() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    NotificationTransport smtp = populatedSmtp();
    NotificationTransport matrix = populatedMatrix();

    String smtpJson = mapper.writeValueAsString(NotificationTransportReadIO.from(smtp));
    String matrixJson = mapper.writeValueAsString(NotificationTransportReadIO.from(matrix));

    // Values must not appear.
    assertFalse(smtpJson.contains(SECRET_PASSWORD),
        "SMTP read JSON leaked smtpPassword value: " + smtpJson);
    assertFalse(matrixJson.contains(SECRET_TOKEN),
        "Matrix read JSON leaked matrixAccessToken value: " + matrixJson);

    // Field NAMES must not appear either — defence in depth against a
    // future refactor that adds a nullable field with the same name.
    assertFalse(smtpJson.toLowerCase().contains("password"),
        "SMTP read JSON contains the substring 'password': " + smtpJson);
    assertFalse(matrixJson.toLowerCase().contains("accesstoken"),
        "Matrix read JSON contains the substring 'accessToken': " + matrixJson);

    // Sanity — the non-secret fields DO appear.
    assertTrue(smtpJson.contains("smtp.example.org"));
    assertTrue(smtpJson.contains("noreply@example.org"));
    assertTrue(matrixJson.contains("matrix.dlr.de"));
    assertTrue(matrixJson.contains("!ops:matrix.dlr.de"));
  }

  @Test
  void from_carriesNonSecretFields() {
    NotificationTransport t = populatedSmtp();
    NotificationTransportReadIO io = NotificationTransportReadIO.from(t);

    assertEquals("app-smtp-1", io.appId());
    assertEquals("SMTP", io.kind());
    assertEquals("Primary SMTP relay", io.name());
    assertTrue(io.enabled());
    assertEquals("OK", io.lastTestResult());
    assertEquals("smtp.example.org", io.smtpHost());
    assertEquals(587, io.smtpPort());
    assertEquals("noreply", io.smtpUsername());
    assertEquals("noreply@example.org", io.smtpFrom());
    assertEquals(Boolean.TRUE, io.smtpTls());
  }

  @Test
  void lastTestedAt_isRenderedAsIso8601() {
    // 1_700_000_000_000 ms = 2023-11-14T22:13:20Z
    NotificationTransport t = populatedSmtp();
    NotificationTransportReadIO io = NotificationTransportReadIO.from(t);
    assertEquals("2023-11-14T22:13:20Z", io.lastTestedAt());
  }

  @Test
  void lastTestedAt_isNullWhenNotSet() {
    NotificationTransport t = populatedSmtp();
    t.setLastTestedAt(null);
    NotificationTransportReadIO io = NotificationTransportReadIO.from(t);
    assertNull(io.lastTestedAt());
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static NotificationTransport populatedSmtp() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-smtp-1");
    t.setKind(TransportKind.SMTP.name());
    t.setName("Primary SMTP relay");
    t.setEnabled(true);
    t.setLastTestResult("OK");
    t.setLastTestedAt(1_700_000_000_000L);
    t.setSmtpHost("smtp.example.org");
    t.setSmtpPort(587);
    t.setSmtpUsername("noreply");
    t.setSmtpPassword(SECRET_PASSWORD);
    t.setSmtpFrom("noreply@example.org");
    t.setSmtpTls(true);
    return t;
  }

  private static NotificationTransport populatedMatrix() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-matrix-1");
    t.setKind(TransportKind.MATRIX.name());
    t.setName("DLR Matrix ops room");
    t.setEnabled(true);
    t.setMatrixHomeserver("https://matrix.dlr.de");
    t.setMatrixAccessToken(SECRET_TOKEN);
    t.setMatrixDefaultRoom("!ops:matrix.dlr.de");
    return t;
  }
}
