package de.dlr.shepard.v2.notifications.io;

import de.dlr.shepard.v2.notifications.entities.Notification;
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

  @Schema(description = "Milliseconds since Unix epoch when this notification was created.", example = "1751400000000")
  private Long createdAtMillis;

  @Schema(description = "Milliseconds since Unix epoch after which this notification is considered expired. Null means it never expires.", nullable = true, example = "1751486400000")
  private Long expiresAtMillis;

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
      n.getCreatedAtMillis(),
      n.getExpiresAtMillis()
    );
  }
}
