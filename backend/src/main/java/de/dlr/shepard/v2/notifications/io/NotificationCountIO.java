package de.dlr.shepard.v2.notifications.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Wire shape for {@code GET /v2/notifications/count}. */
@Schema(description = "Notification count response containing the number of unread notifications for the current user.")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationCountIO {

  private long unread;
}
