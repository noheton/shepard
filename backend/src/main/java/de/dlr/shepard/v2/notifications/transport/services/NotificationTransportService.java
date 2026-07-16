package de.dlr.shepard.v2.notifications.transport.services;

import de.dlr.shepard.v2.notifications.transport.daos.NotificationTransportDAO;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * NTF1-BACKEND-LIST / -CRUD — business logic for the transport-CRUD
 * surface.
 *
 * <p>Thin wrapper over {@link NotificationTransportDAO} so the REST
 * resource stays focused on wire concerns (status codes, content-type,
 * RFC 7396 merge-patch resolution). The service is the place to add
 * cross-cutting policy later — validation against {@code kind}-specific
 * required fields, audit-trail annotation, etc.
 */
@RequestScoped
public class NotificationTransportService {

  @Inject
  NotificationTransportDAO dao;

  /**
   * Return all configured transports, ordered name-ascending so the
   * admin pane renders a stable list.
   *
   * @deprecated Use {@link #count()} + {@link #listPaged(int, int)} instead.
   *   Kept for existing callers; not used by the REST list endpoint since
   *   APISIMP-NOTIF-TRANSPORT-INMEM.
   */
  @Deprecated(forRemoval = false)
  public List<NotificationTransport> listAll() {
    var all = dao.findAll();
    if (all == null || all.isEmpty()) {
      return List.of();
    }
    List<NotificationTransport> sorted = new ArrayList<>(all);
    sorted.sort(Comparator.comparing(
        NotificationTransport::getName,
        Comparator.nullsLast(Comparator.naturalOrder())));
    return sorted;
  }

  /** Total count of :NotificationTransport nodes. Pushed to the DB. */
  public long count() {
    return dao.countAll();
  }

  /**
   * Returns a bounded, name-ASC page of transports. Two DB queries
   * (count + list) replace the prior load-all + subList approach.
   *
   * @param page     zero-based page index
   * @param pageSize number of items per page
   */
  public List<NotificationTransport> listPaged(int page, int pageSize) {
    long skip = (long) page * pageSize;
    return dao.listPaged(skip, pageSize);
  }

  /** Lookup by appId. {@link Optional#empty()} when none matches. */
  public Optional<NotificationTransport> findByAppId(String appId) {
    return dao.findByAppId(appId);
  }

  /**
   * Persist {@code transport} (insert or update). Mints an appId via
   * {@code GenericDAO.createOrUpdate} when null.
   */
  public NotificationTransport save(NotificationTransport transport) {
    NotificationTransport saved = dao.createOrUpdate(transport);
    Log.infof("NTF1: saved :NotificationTransport (appId=%s, kind=%s, name=%s, enabled=%s)",
        saved.getAppId(), saved.getKind(), saved.getName(), saved.isEnabled());
    return saved;
  }

  /**
   * Delete by appId. Returns {@code true} when a row was found and
   * removed, {@code false} otherwise.
   */
  public boolean deleteByAppId(String appId) {
    boolean deleted = dao.deleteByAppId(appId);
    if (deleted) {
      Log.infof("NTF1: deleted :NotificationTransport (appId=%s)", appId);
    }
    return deleted;
  }
}
