package de.dlr.shepard.v2.notifications.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Wire shape for {@code GET /v2/notifications/count}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationCountIO {

  private long unread;
}
