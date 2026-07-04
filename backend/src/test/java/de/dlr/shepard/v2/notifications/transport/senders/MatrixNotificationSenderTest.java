package de.dlr.shepard.v2.notifications.transport.senders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-MATRIX — sender tests using a subclass-override seam.
 *
 * <p>Covers: identity; URL construction; happy path (2xx → true);
 * 4xx/5xx → false; IOException → false; missing-field guards.
 */
class MatrixNotificationSenderTest {

  @Test
  void kind_isMATRIX() {
    assertEquals(TransportKind.MATRIX, new MatrixNotificationSender().kind());
  }

  @Test
  void buildSendUrl_constructsCanonicalMatrixPath() {
    String url = MatrixNotificationSender.buildSendUrl(
        "https://matrix.dlr.de",
        "!abc:matrix.dlr.de",
        "shepard-txn-1");
    assertTrue(url.startsWith("https://matrix.dlr.de/_matrix/client/v3/rooms/"),
        "expected canonical Matrix client-server v3 path, got: " + url);
    assertTrue(url.contains("send/m.room.message/"),
        "expected /send/m.room.message/{txnId} segment");
    // Room IDs contain ! and : — must be URL-encoded.
    assertTrue(url.contains("%21abc%3Amatrix.dlr.de"),
        "room id segment must be URL-encoded: " + url);
  }

  @Test
  void buildSendUrl_stripsTrailingSlashOnHomeserver() {
    String url = MatrixNotificationSender.buildSendUrl(
        "https://matrix.dlr.de/",
        "!r:matrix.dlr.de",
        "t");
    assertTrue(url.startsWith("https://matrix.dlr.de/_matrix"),
        "trailing slash should not double up: " + url);
  }

  @Test
  void send_happyPath_returnsTrueOn200() throws Exception {
    AtomicReference<HttpRequest> captured = new AtomicReference<>();
    MatrixNotificationSender sender = new MatrixNotificationSender() {
      @Override
      HttpResponse<String> doSend(HttpRequest req) {
        captured.set(req);
        @SuppressWarnings("unchecked")
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"event_id\":\"$evt:matrix.dlr.de\"}");
        return ok;
      }
    };

    NotificationTransport t = matrixTransport();
    NotificationMessage msg = new NotificationMessage(
        null,                                  // recipient null → use default room
        "Alert",
        "TR-004 anomaly detected",
        "https://shepard.example.org/anomaly/42");

    boolean ok = sender.send(t, msg);
    assertTrue(ok);

    HttpRequest req = captured.get();
    assertNotNull(req);
    assertEquals("PUT", req.method());
    assertTrue(req.uri().toString().contains("/send/m.room.message/"));
    assertEquals("Bearer test-token", req.headers().firstValue("Authorization").orElse(""));
    assertEquals("application/json", req.headers().firstValue("Content-Type").orElse(""));
  }

  @Test
  void send_returnsFalseOn403() throws Exception {
    MatrixNotificationSender sender = new MatrixNotificationSender() {
      @Override
      HttpResponse<String> doSend(HttpRequest req) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> forbidden = mock(HttpResponse.class);
        when(forbidden.statusCode()).thenReturn(403);
        when(forbidden.body()).thenReturn("{\"errcode\":\"M_FORBIDDEN\"}");
        return forbidden;
      }
    };
    boolean ok = sender.send(matrixTransport(), new NotificationMessage(
        null, "Alert", "body", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseOn503() throws Exception {
    MatrixNotificationSender sender = new MatrixNotificationSender() {
      @Override
      HttpResponse<String> doSend(HttpRequest req) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> unavailable = mock(HttpResponse.class);
        when(unavailable.statusCode()).thenReturn(503);
        when(unavailable.body()).thenReturn("homeserver unavailable");
        return unavailable;
      }
    };
    boolean ok = sender.send(matrixTransport(), new NotificationMessage(
        null, "Alert", "body", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseOnIOException() {
    MatrixNotificationSender sender = new MatrixNotificationSender() {
      @Override
      HttpResponse<String> doSend(HttpRequest req) throws IOException {
        throw new IOException("simulated network down");
      }
    };
    boolean ok = sender.send(matrixTransport(), new NotificationMessage(
        null, "Alert", "body", null));
    assertFalse(ok, "IOException → false, not propagated");
  }

  @Test
  void send_returnsFalseWhenHomeserverMissing() {
    MatrixNotificationSender sender = new MatrixNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setMatrixAccessToken("token");
    t.setMatrixDefaultRoom("!r:hs");
    boolean ok = sender.send(t, new NotificationMessage(null, "t", "b", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseWhenAccessTokenMissing() {
    MatrixNotificationSender sender = new MatrixNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setMatrixHomeserver("https://m");
    t.setMatrixDefaultRoom("!r:hs");
    boolean ok = sender.send(t, new NotificationMessage(null, "t", "b", null));
    assertFalse(ok);
  }

  @Test
  void send_returnsFalseWhenNoRoom() {
    MatrixNotificationSender sender = new MatrixNotificationSender();
    NotificationTransport t = new NotificationTransport();
    t.setMatrixHomeserver("https://m");
    t.setMatrixAccessToken("tk");
    // No default room AND message has no recipient.
    boolean ok = sender.send(t, new NotificationMessage(null, "t", "b", null));
    assertFalse(ok);
  }

  @Test
  void send_usesMessageRecipientWhenSet_overridingDefaultRoom() throws Exception {
    AtomicReference<HttpRequest> captured = new AtomicReference<>();
    MatrixNotificationSender sender = new MatrixNotificationSender() {
      @Override
      HttpResponse<String> doSend(HttpRequest req) {
        captured.set(req);
        @SuppressWarnings("unchecked")
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        return ok;
      }
    };
    NotificationTransport t = matrixTransport();
    sender.send(t, new NotificationMessage("!override:hs", "t", "b", null));
    String url = captured.get().uri().toString();
    // !override:hs gets URL-encoded to %21override%3Ahs.
    assertTrue(url.contains("%21override"),
        "expected per-message room override in URL: " + url);
    assertFalse(url.contains("%21ops"),
        "default room should not appear when recipient overrides: " + url);
  }

  @Test
  void buildBody_combinesTitleAndBodyAndUrl() {
    MatrixNotificationSender sender = new MatrixNotificationSender();
    String body = sender.buildBody(new NotificationMessage(
        null, "Alert", "TR-004 anomaly", "https://shepard/42"));
    assertTrue(body.contains("\"msgtype\":\"m.text\""));
    assertTrue(body.contains("Alert"));
    assertTrue(body.contains("TR-004 anomaly"));
    assertTrue(body.contains("https://shepard/42"));
  }

  @Test
  void send_throwsIllegalArgumentOnNullArgs() {
    MatrixNotificationSender sender = new MatrixNotificationSender();
    assertThrows(IllegalArgumentException.class, () -> sender.send(null, null));
  }

  private static NotificationTransport matrixTransport() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-mx");
    t.setKind(TransportKind.MATRIX.name());
    t.setMatrixHomeserver("https://matrix.dlr.de");
    t.setMatrixAccessToken("test-token");
    t.setMatrixDefaultRoom("!ops:matrix.dlr.de");
    return t;
  }
}
