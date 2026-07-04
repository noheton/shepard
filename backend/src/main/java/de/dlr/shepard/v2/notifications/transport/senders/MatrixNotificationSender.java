package de.dlr.shepard.v2.notifications.transport.senders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationSender;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * NTF1-BACKEND-MATRIX — Matrix room.send sender (client-server API v3).
 *
 * <p>Posts an {@code m.room.message} event to the configured Matrix
 * homeserver via:
 *
 * <pre>
 *   PUT {homeserver}/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
 * </pre>
 *
 * <p>The destination room is the {@code matrixDefaultRoom} on the
 * transport, unless the {@link NotificationMessage} carries a non-null
 * recipient — in which case the recipient is treated as the per-message
 * room override.
 *
 * <p>Uses the JDK's {@link HttpClient} (not a Quarkus REST client) so
 * the homeserver URL can be operator-configured per-transport at runtime
 * rather than baked in at compile time. {@link #doSend(HttpRequest)} is
 * the protected seam tests override to avoid network calls.
 */
@ApplicationScoped
public class MatrixNotificationSender implements NotificationSender {

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public TransportKind kind() {
    return TransportKind.MATRIX;
  }

  @Override
  public boolean send(NotificationTransport transport, NotificationMessage message) {
    if (transport == null || message == null) {
      throw new IllegalArgumentException("transport and message must not be null");
    }
    if (transport.getMatrixHomeserver() == null || transport.getMatrixHomeserver().isBlank()) {
      Log.warnf("NTF1-MATRIX: transport appId=%s missing matrixHomeserver — cannot send",
          transport.getAppId());
      return false;
    }
    if (transport.getMatrixAccessToken() == null || transport.getMatrixAccessToken().isBlank()) {
      Log.warnf("NTF1-MATRIX: transport appId=%s missing matrixAccessToken — cannot send",
          transport.getAppId());
      return false;
    }
    String roomId = message.recipient() != null && !message.recipient().isBlank()
        ? message.recipient()
        : transport.getMatrixDefaultRoom();
    if (roomId == null || roomId.isBlank()) {
      Log.warnf("NTF1-MATRIX: no room (neither message.recipient nor matrixDefaultRoom) — skipping send for transport appId=%s",
          transport.getAppId());
      return false;
    }

    try {
      String body = buildBody(message);
      String txnId = "shepard-" + UUID.randomUUID();
      String url = buildSendUrl(transport.getMatrixHomeserver(), roomId, txnId);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Authorization", "Bearer " + transport.getMatrixAccessToken())
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(15))
          .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> resp = doSend(req);
      int status = resp.statusCode();
      if (status >= 200 && status < 300) {
        Log.debugf("NTF1-MATRIX: delivered notification to room=%s (status=%d)", roomId, status);
        return true;
      }
      Log.warnf("NTF1-MATRIX: send to room=%s returned status=%d body=%s",
          roomId, status, resp.body());
      return false;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      Log.warnf(e, "NTF1-MATRIX: send failed for transport appId=%s", transport.getAppId());
      return false;
    }
  }

  /** Construct the m.room.message JSON body — body + msgtype only, v1. */
  String buildBody(NotificationMessage message) {
    ObjectNode obj = mapper.createObjectNode();
    obj.put("msgtype", "m.text");
    String body = message.title() == null ? "" : message.title();
    if (message.body() != null && !message.body().isBlank()) {
      body = body + (body.isBlank() ? "" : ": ") + message.body();
    }
    if (message.actionUrl() != null && !message.actionUrl().isBlank()) {
      body = body + " " + message.actionUrl();
    }
    obj.put("body", body);
    return obj.toString();
  }

  /**
   * Construct the full PUT URL per the Matrix client-server API v3.
   * Path segments are URL-encoded to handle room IDs containing {@code !}
   * and {@code :}. Package-private for tests.
   */
  static String buildSendUrl(String homeserver, String roomId, String txnId) {
    String base = homeserver.endsWith("/")
        ? homeserver.substring(0, homeserver.length() - 1)
        : homeserver;
    return base
        + "/_matrix/client/v3/rooms/"
        + URLEncoder.encode(roomId, StandardCharsets.UTF_8)
        + "/send/m.room.message/"
        + URLEncoder.encode(txnId, StandardCharsets.UTF_8);
  }

  /**
   * Send the HTTP request — package-private seam tests override.
   */
  HttpResponse<String> doSend(HttpRequest req) throws IOException, InterruptedException {
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }
}
