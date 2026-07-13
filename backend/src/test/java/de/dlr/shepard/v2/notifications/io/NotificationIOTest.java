package de.dlr.shepard.v2.notifications.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.v2.notifications.entities.Notification;
import org.junit.jupiter.api.Test;

/** Unit tests for APISIMP-MULTI-IO-EPOCH-MS-TO-ISO: NotificationIO epoch-ms → ISO 8601. */
class NotificationIOTest {

  private Notification buildNotification(Long createdAtMillis, Long expiresAtMillis) {
    Notification n = new Notification();
    n.setAppId("notif-1");
    n.setAudience("alice");
    n.setCategory("system");
    n.setSource("test");
    n.setTitle("Hello");
    n.setCreatedAtMillis(createdAtMillis);
    n.setExpiresAtMillis(expiresAtMillis);
    return n;
  }

  @Test
  void createdAtIsRenderedAsIso8601() {
    // 2025-07-01T00:00:00Z = 1751328000000 ms
    NotificationIO io = NotificationIO.from(buildNotification(1751328000000L, null));
    assertEquals("2025-07-01T00:00:00Z", io.getCreatedAt());
  }

  @Test
  void expiresAtIsRenderedAsIso8601WhenSet() {
    NotificationIO io = NotificationIO.from(buildNotification(1751328000000L, 1751414400000L));
    assertEquals("2025-07-02T00:00:00Z", io.getExpiresAt());
  }

  @Test
  void expiresAtIsNullWhenNotSet() {
    NotificationIO io = NotificationIO.from(buildNotification(1751328000000L, null));
    assertNull(io.getExpiresAt());
  }

  @Test
  void epochZeroRendersAsUnixEpoch() {
    NotificationIO io = NotificationIO.from(buildNotification(0L, null));
    assertEquals("1970-01-01T00:00:00Z", io.getCreatedAt());
  }
}
