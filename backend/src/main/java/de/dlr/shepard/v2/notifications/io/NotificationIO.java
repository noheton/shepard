package de.dlr.shepard.v2.notifications.io;

import de.dlr.shepard.v2.notifications.entities.Notification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Wire shape for a single notification returned by {@code GET /v2/notifications}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationIO {

  private String appId;
  private String audience;
  private String category;
  private String source;
  private String title;
  private String body;
  private String actionUrl;
  private boolean read;
  private Long createdAtMillis;
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
