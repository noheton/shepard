package de.dlr.shepard.v2.collectionwatchers.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.collectionwatchers.daos.CollectionWatcherDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * CW1 — business logic for user-level Collection watching.
 *
 * <p>A user can "watch" a Collection they have Read access to. When a new
 * top-level DataObject is added to that Collection, all watchers receive an
 * in-app notification (NTF1a). The notification dispatch is best-effort:
 * if the notification system is unavailable the DataObject creation is not
 * blocked.
 *
 * <p>Auth rules:
 * <ul>
 *   <li>{@code GET /watches} — requires Read on the Collection; returns
 *       only watcher count + caller's own status by default.</li>
 *   <li>{@code GET /watches/me} — any authenticated user; 404 if not watching.</li>
 *   <li>{@code POST /watches} — requires Read on the Collection (idempotent).</li>
 *   <li>{@code DELETE /watches/me} — any authenticated user; idempotent.</li>
 * </ul>
 */
@RequestScoped
public class CollectionWatcherService {

  @Inject
  CollectionWatcherDAO dao;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  UserService userService;

  @Inject
  NotificationService notificationService;

  // ─── Public API ───────────────────────────────────────────────────────────

  /**
   * List all watchers for a collection.
   *
   * @param collectionAppId the collection's appId
   * @param caller          authenticated username
   */
  public List<CollectionWatcherIO> list(String collectionAppId, String caller) {
    assertCollectionReadable(collectionAppId, caller);
    return dao.findByCollectionAppId(collectionAppId)
      .stream()
      .map(CollectionWatcherIO::from)
      .toList();
  }

  /**
   * Return the caller's own watch record, or empty if not watching.
   *
   * @param collectionAppId the collection's appId
   * @param caller          authenticated username
   */
  public Optional<CollectionWatcherIO> getMe(String collectionAppId, String caller) {
    CollectionWatcher w = dao.findByUsernameAndCollection(caller, collectionAppId);
    return w == null ? Optional.empty() : Optional.of(CollectionWatcherIO.from(w));
  }

  /**
   * Start watching a collection. Idempotent on (caller, collectionAppId).
   *
   * @param collectionAppId the collection's appId
   * @param caller          authenticated username
   * @return the existing or new watcher record
   */
  public CollectionWatcherIO watch(String collectionAppId, String caller) {
    assertCollectionReadable(collectionAppId, caller);

    // Idempotent: return existing record without error.
    CollectionWatcher existing = dao.findByUsernameAndCollection(caller, collectionAppId);
    if (existing != null) {
      return CollectionWatcherIO.from(existing);
    }

    CollectionWatcher w = new CollectionWatcher();
    w.setUsername(caller);
    w.setCollectionAppId(collectionAppId);
    w.setSince(System.currentTimeMillis());
    CollectionWatcher saved = dao.createOrUpdate(w);
    Log.infof("CW1: %s is now watching collection %s", caller, collectionAppId);
    return CollectionWatcherIO.from(saved);
  }

  /**
   * Stop watching a collection. Idempotent.
   *
   * @param collectionAppId the collection's appId
   * @param caller          authenticated username
   */
  public void unwatch(String collectionAppId, String caller) {
    CollectionWatcher existing = dao.findByUsernameAndCollection(caller, collectionAppId);
    if (existing == null) {
      // Already not watching — idempotent, no error.
      return;
    }
    dao.deleteByNeo4jId(existing.getId());
    Log.infof("CW1: %s stopped watching collection %s", caller, collectionAppId);
  }

  /**
   * Notify all watchers of a collection that a new DataObject was added.
   *
   * <p>Best-effort: errors are logged but do not propagate to callers.
   *
   * @param collectionAppId the collection's appId
   * @param collectionName  human-readable name for the notification
   * @param dataObjectName  name of the new DataObject
   * @param collectionOgmId legacy numeric id for the URL deep-link
   */
  public void notifyWatchersOfNewDataObject(
    String collectionAppId,
    String collectionName,
    String dataObjectName,
    long collectionOgmId
  ) {
    try {
      List<String> watchers = dao.findWatcherUsernamesByCollectionAppId(collectionAppId);
      if (watchers.isEmpty()) return;

      String title = "New data object in \"" + (collectionName != null ? collectionName : collectionAppId) + "\"";
      String body = "**" + (dataObjectName != null ? dataObjectName : "Unnamed") +
        "** was added to [" + (collectionName != null ? collectionName : collectionAppId) +
        "](/collections/" + collectionOgmId + ").";
      String actionUrl = "/collections/" + collectionOgmId;

      for (String username : watchers) {
        notificationService.publish(
          NotificationService.AUDIENCE_USER,
          username,
          NotificationService.CATEGORY_INFO,
          "collection-watch",
          title,
          body,
          actionUrl
        );
      }
      Log.debugf("CW1: notified %d watcher(s) of new DataObject in collection %s", watchers.size(), collectionAppId);
    } catch (Exception e) {
      // Best-effort — do not block DataObject creation.
      Log.warnf("CW1: failed to notify watchers of collection %s: %s", collectionAppId, e.getMessage());
    }
  }

  // ─── Internal helpers ─────────────────────────────────────────────────────

  private void assertCollectionReadable(String collectionAppId, String caller) {
    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      throw new NotFoundException("No Collection with appId " + collectionAppId);
    }
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      throw new ForbiddenException("Caller lacks Read on collection " + collectionAppId);
    }
  }
}
