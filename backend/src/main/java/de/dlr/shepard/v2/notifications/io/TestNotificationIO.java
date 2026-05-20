package de.dlr.shepard.v2.notifications.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code POST /v2/admin/test-notification}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestNotificationIO {

  @Schema(
    description = "Audience: USER (requires targetUsername), INSTANCE_ADMIN, or ALL.",
    enumeration = { "USER", "INSTANCE_ADMIN", "ALL" },
    defaultValue = "INSTANCE_ADMIN"
  )
  private String audience = "INSTANCE_ADMIN";

  @Schema(description = "Required when audience is USER. Username of the target recipient.")
  private String targetUsername;

  @Schema(
    description = "Severity: INFO, WARNING, or ACTION_REQUIRED.",
    enumeration = { "INFO", "WARNING", "ACTION_REQUIRED" },
    defaultValue = "INFO"
  )
  private String category = "INFO";

  @Schema(description = "Notification title.", example = "Test notification from admin")
  private String title = "Test notification";

  @Schema(description = "Markdown body text.", example = "This is a **test** notification.")
  private String body = "This is a test notification sent from the admin panel.";

  @Schema(description = "Optional deep-link URL for the Go button.", nullable = true)
  private String actionUrl;
}
