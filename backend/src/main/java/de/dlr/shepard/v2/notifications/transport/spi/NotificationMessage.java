package de.dlr.shepard.v2.notifications.transport.spi;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — outbound message envelope passed to the
 * {@link NotificationSender} SPI.
 *
 * <p>Substrate-agnostic: an SMTP sender renders these as
 * {@code Subject:} + plain-text body; a Matrix sender wraps them in an
 * {@code m.room.message} event; an in-app sender writes a
 * {@code :Notification} node. The transport is responsible for any
 * markdown rendering.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code recipient} — kind-specific routing hint: an email address
 *       for SMTP, a Matrix room id for MATRIX (overrides the transport's
 *       {@code matrixDefaultRoom} when present), or a username for INAPP.
 *       Nullable — falls back to the transport's default routing.</li>
 *   <li>{@code title} — short subject / summary line. Required.</li>
 *   <li>{@code body} — markdown body. Required.</li>
 *   <li>{@code actionUrl} — optional deep-link the recipient can follow.</li>
 * </ul>
 */
public record NotificationMessage(
    String recipient,
    String title,
    String body,
    String actionUrl
) {
  /** Convenience for the per-transport smoke-test path. */
  public static NotificationMessage forTest(String title, String body) {
    return new NotificationMessage(null, title, body, null);
  }
}
