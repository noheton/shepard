package de.dlr.shepard.v2.notifications.io;

import de.dlr.shepard.v2.notifications.entities.Notification;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Wire shape for a single notification returned by {@code GET /v2/notifications}. */
@Schema(description = "A single in-app notification for the current user, including category, source, and read status.")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationIO {

  @Schema(description = "Stable application-level identifier (UUID v7). Read-only.", readOnly = true)
  private String appId;

  @Schema(description = "Target audience for this notification (e.g. a username or 'everyone').")
  private String audience;

  @Schema(description = "Category tag used for client-side filtering (e.g. 'import', 'annotation', 'system').")
  private String category;

  @Schema(description = "Identifier of the service or plugin that generated this notification.")
  private String source;

  @Schema(description = "Short human-readable title shown in the notification list.")
  private String title;

  @Schema(description = "Full body text of the notification. May be null for title-only notifications.", nullable = true)
  private String body;

  @Schema(description = "Optional deep-link URL the user can follow to act on this notification.", nullable = true)
  private String actionUrl;

  @Schema(description = "Whether the current user has marked this notification as read.")
  private boolean read;

  @Schema(description = "ISO 8601 UTC timestamp when this notification was created.", example = "2025-07-01T12:00:00Z")
  private String createdAt;

  @Schema(description = "ISO 8601 UTC timestamp after which this notification is considered expired. Null means it never expires.", nullable = true, example = "2025-07-02T12:00:00Z")
  private String expiresAt;

  public static NotificationIO from(Notification n) {
    return new NotificationIO(
      n.getAppId(),
      n.getAudience(),
      n.getCategory(),
      n.getSource(),
      n.getTitle(),
      n.getBody(),
      n.getActionUrl(),
      n.isRead(),
      toIso(n.getCreatedAtMillis()),
      toIso(n.getExpiresAtMillis())
    );
  }

  private static String toIso(Long epochMs) {
    if (epochMs == null) return null;
    return DateTimeFormatter.ISO_INSTANT.format(
      Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC)
    );
  }
}
