package de.dlr.shepard.v2.notifications.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * One in-app notification delivered to a user or a role group.
 * Designed in {@code aidocs/ntf/NTF1a}.
 *
 * <p>Audience semantics:
 * <ul>
 *   <li>{@code USER} — targeted at a single user; {@code targetUsername} must be set.</li>
 *   <li>{@code INSTANCE_ADMIN} — broadcast to all instance-admins; {@code targetUsername} is null.</li>
 *   <li>{@code ALL} — broadcast to every authenticated user; {@code targetUsername} is null.</li>
 * </ul>
 *
 * <p>Read state ({@code read}) is tracked per-node. Broadcasts (INSTANCE_ADMIN / ALL)
 * are fanned out to per-user nodes at publish time so each reader has an independent
 * read flag — the service layer handles the fan-out.
 */
@NodeEntity
@Data
@NoArgsConstructor
public class Notification implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /**
   * Delivery scope: {@code USER}, {@code INSTANCE_ADMIN}, or {@code ALL}.
   * Stored even after fan-out so the audit trail can show the original intent.
   */
  @Property("audience")
  private String audience;

  /**
   * Username of the intended recipient. Set only when {@code audience == USER}.
   * The index on this property makes per-user list queries O(log n).
   */
  @Property("targetUsername")
  private String targetUsername;

  /**
   * Severity / urgency hint for the UI: {@code INFO}, {@code WARNING},
   * or {@code ACTION_REQUIRED}.
   */
  @Property("category")
  private String category;

  /**
   * Identifies which subsystem produced this notification — e.g.
   * {@code "system"}, {@code "plugin:shepard-plugin-matrix"},
   * {@code "download-prepare"}. Free-form; used for filtering.
   */
  @Property("source")
  private String source;

  /** Short title rendered in the notification list. */
  @Property("title")
  private String title;

  /** Markdown body rendered in the expanded notification panel. */
  @Property("body")
  private String body;

  /**
   * Optional deep-link that the notification panel's "Go" button opens.
   * Relative paths are resolved against the frontend origin.
   */
  @Property("actionUrl")
  private String actionUrl;

  /** Whether the recipient has read (dismissed) this notification. */
  @Property("read")
  private boolean read;

  /** Epoch millis when this notification was created. */
  @Property("createdAtMillis")
  private Long createdAtMillis;

  /**
   * Optional epoch millis after which this notification should be hidden.
   * {@code null} means the notification does not expire.
   */
  @Property("expiresAtMillis")
  private Long expiresAtMillis;

  public Notification(
    String audience,
    String targetUsername,
    String category,
    String source,
    String title,
    String body,
    String actionUrl
  ) {
    this.audience = audience;
    this.targetUsername = targetUsername;
    this.category = category;
    this.source = source;
    this.title = title;
    this.body = body;
    this.actionUrl = actionUrl;
    this.read = false;
    this.createdAtMillis = System.currentTimeMillis();
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Notification other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
