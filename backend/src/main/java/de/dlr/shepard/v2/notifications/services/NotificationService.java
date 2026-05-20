package de.dlr.shepard.v2.notifications.services;

import de.dlr.shepard.v2.notifications.daos.NotificationDAO;
import jakarta.ws.rs.NotFoundException;
import de.dlr.shepard.v2.notifications.entities.Notification;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Business logic for in-app notifications (NTF1a).
 *
 * <p>The {@link #publish} method is the SPI entry point that plugins and other
 * services call to deliver a notification. It writes synchronously to Neo4j
 * (no queue in v1) and returns immediately; the caller does not need to await
 * delivery.
 *
 * <p>Frontend delivery is via 30-second polling of
 * {@code GET /v2/notifications/count} and {@code GET /v2/notifications}.
 */
@RequestScoped
public class NotificationService {

  /** Audience constants — also used by {@code NotificationRest}. */
  public static final String AUDIENCE_USER = "USER";
  public static final String AUDIENCE_INSTANCE_ADMIN = "INSTANCE_ADMIN";
  public static final String AUDIENCE_ALL = "ALL";

  /** Category constants. */
  public static final String CATEGORY_INFO = "INFO";
  public static final String CATEGORY_WARNING = "WARNING";
  public static final String CATEGORY_ACTION_REQUIRED = "ACTION_REQUIRED";

  @Inject
  NotificationDAO dao;

  /**
   * Publish a notification.
   *
   * @param audience        one of {@code USER}, {@code INSTANCE_ADMIN}, {@code ALL}
   * @param targetUsername  recipient username when {@code audience == USER}; null otherwise
   * @param category        one of {@code INFO}, {@code WARNING}, {@code ACTION_REQUIRED}
   * @param source          producer identifier, e.g. {@code "system"} or {@code "plugin:xyz"}
   * @param title           short title for the notification list
   * @param body            markdown body shown in the expanded panel
   * @param actionUrl       optional deep-link (relative path) for the "Go" button; null if unused
   */
  public Notification publish(
    String audience,
    String targetUsername,
    String category,
    String source,
    String title,
    String body,
    String actionUrl
  ) {
    Notification n = new Notification(audience, targetUsername, category, source, title, body, actionUrl);
    dao.createOrUpdate(n);
    Log.debugf("NTF1a: published %s notification '%s' to %s (source=%s)", category, title, audience, source);
    return n;
  }

  /** List all notifications visible to the given user. */
  public List<Notification> listForUser(String username, boolean isAdmin) {
    return dao.listForUser(username, isAdmin);
  }

  /** Count unread notifications visible to the given user. */
  public long countUnread(String username, boolean isAdmin) {
    return dao.countUnread(username, isAdmin);
  }

  /**
   * Mark a notification as read. Returns the updated notification.
   * Throws {@link ShepardNotFoundException} if the notification does not exist
   * or is not visible to the caller.
   */
  public Notification markRead(String appId, String username, boolean isAdmin) {
    Notification n = dao.findByAppIdForUser(appId, username, isAdmin)
      .orElseThrow(() -> new NotFoundException("Notification not found: " + appId));
    n.setRead(true);
    dao.createOrUpdate(n);
    return n;
  }

  /**
   * Dismiss (delete) a notification. Throws {@link NotFoundException} if
   * the notification does not exist or is not visible to the caller.
   */
  public void dismiss(String appId, String username, boolean isAdmin) {
    Notification n = dao.findByAppIdForUser(appId, username, isAdmin)
      .orElseThrow(() -> new NotFoundException("Notification not found: " + appId));
    dao.deleteByNeo4jId(n.getId());
  }
}
