package de.dlr.shepard.data.hdf.permissions;

import de.dlr.shepard.auth.permission.events.PermissionsChangedEvent;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A5b Phase 2 — pipes shepard permission changes through to the HSDS
 * sidecar's domain ACL via a CDI {@code @Observes} hook on
 * {@link PermissionsChangedEvent}
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §6).
 *
 * <h3>Sync direction</h3>
 *
 * shepard is the <strong>source of truth</strong>. This bridge only
 * flows changes one way (shepard → HSDS). Direct HSDS-side ACL edits
 * get clobbered the next time shepard fires a permissions event for
 * the same domain — operators are explicitly told not to mutate HSDS
 * ACLs out of band (see {@code docs/reference/hdf-container.md}).
 *
 * <h3>Best-effort semantics</h3>
 *
 * Sync failures <em>never</em> block the user's shepard write. On any
 * sync error we:
 * <ol>
 *   <li>Log at WARN with the entityId / domain.</li>
 *   <li>Queue the event into an in-memory retry list (size capped;
 *       oldest entries are dropped on overflow).</li>
 * </ol>
 *
 * <p>The retry list is a comfort feature for transient HSDS hiccups
 * — when in real trouble the operator runs the rebuild-acls admin
 * endpoint ({@link de.dlr.shepard.v2.admin.hdf.HdfAdminRest}) which
 * re-derives every HSDS ACL from shepard's graph from scratch.
 *
 * <h3>Filtering</h3>
 *
 * Only events with {@code entityKind == "HdfContainer"} touch HSDS.
 * Every other entity-kind is a no-op; observers for those would live
 * in their own bridges. The kind-name filter is the cheap one — we
 * short-circuit before any DB or HTTP I/O.
 *
 * <h3>Toggle</h3>
 *
 * The bridge is registered with {@code @LookupIfProperty(name =
 * "shepard.hdf.enabled", stringValue = "true")} — when the feature
 * is off, this bean is never instantiated and the observer never
 * fires, so no HSDS round-trip is ever attempted.
 */
@ApplicationScoped
@LookupIfProperty(name = "shepard.hdf.enabled", stringValue = "true")
public class HdfPermissionBridge {

  /** Default cap on the in-memory retry list. */
  static final int DEFAULT_RETRY_CAPACITY = 256;

  @Inject
  Instance<HsdsClient> hsdsClientInstance;

  @Inject
  HdfContainerDAO hdfContainerDAO;

  @Inject
  PermissionsService permissionsService;

  private final Deque<RetryEntry> retryQueue = new ArrayDeque<>(DEFAULT_RETRY_CAPACITY);

  private int retryCapacity = DEFAULT_RETRY_CAPACITY;

  /** Test seam — package-private ctor for unit tests. */
  HdfPermissionBridge() {
    // CDI no-arg ctor; fields are @Inject'd.
  }

  HdfPermissionBridge(Instance<HsdsClient> hsdsClientInstance, HdfContainerDAO hdfContainerDAO, PermissionsService permissionsService) {
    this.hsdsClientInstance = hsdsClientInstance;
    this.hdfContainerDAO = hdfContainerDAO;
    this.permissionsService = permissionsService;
  }

  /**
   * CDI observer entry-point. Fires on every
   * {@link PermissionsChangedEvent}; we short-circuit on
   * non-HdfContainer events at the top of the method.
   *
   * <p>{@link TransactionPhase#AFTER_SUCCESS} — only fire on a
   * committed shepard write. If the shepard transaction rolls back,
   * HSDS stays untouched.
   */
  public void onPermissionsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) PermissionsChangedEvent event) {
    if (event == null) return;
    if (!"HdfContainer".equals(event.getEntityKind())) return;
    syncOne(event);
  }

  /**
   * Re-resolve the container, compute the new ACL from shepard's
   * permissions graph, and push to HSDS. Any error is logged and
   * the event is queued for later retry.
   *
   * @return {@code true} if the HSDS write went through; {@code false}
   *         on any error (logged + queued).
   */
  boolean syncOne(PermissionsChangedEvent event) {
    HsdsClient hsds;
    try {
      hsds = requireHsds();
    } catch (IllegalStateException offline) {
      Log.debugf("HSDS feature toggled off; skipping bridge for entityId=%s", event.getEntityId());
      return false;
    }

    HdfContainer container;
    try {
      container = hdfContainerDAO.findByNeo4jId(event.getEntityId());
    } catch (RuntimeException e) {
      Log.warnf(e, "HSDS bridge: failed to reload HdfContainer %s; queueing for retry", event.getEntityId());
      enqueueRetry(event);
      return false;
    }
    if (container == null) {
      Log.debugf("HSDS bridge: no HdfContainer for entityId=%s (deleted?); skipping", event.getEntityId());
      return false;
    }
    if (container.getHsdsDomain() == null || container.getHsdsDomain().isBlank()) {
      Log.warnf("HSDS bridge: HdfContainer entityId=%s has no hsdsDomain; skipping", event.getEntityId());
      return false;
    }

    // Look up the entity's permissions row. The bridge runs as a
    // background hook — it sees the post-commit state via the DAO.
    Optional<Permissions> permsOpt = permissionsService.getPermissionsOfEntityOptional(container.getId());
    if (permsOpt.isEmpty()) {
      // DELETED events (or orphan rows) — best we can do is clear non-owner ACEs.
      try {
        hsds.clearDomainAcl(container.getHsdsDomain());
        return true;
      } catch (RuntimeException e) {
        Log.warnf(e, "HSDS bridge: clearDomainAcl failed for domain=%s; queueing", container.getHsdsDomain());
        enqueueRetry(event);
        return false;
      }
    }

    Permissions perms = permsOpt.get();
    String owner = perms.getOwner() == null ? null : perms.getOwner().getUsername();
    Set<String> readers = collectUsernames(perms.getReader(), expandGroups(perms.getReaderGroups()));
    Set<String> writers = collectUsernames(perms.getWriter(), expandGroups(perms.getWriterGroups()));
    Set<String> managers = collectUsernames(perms.getManager(), List.of());

    String fingerprint = HsdsClient.fingerprintAcl(owner, readers, writers, managers);
    try {
      hsds.setDomainAcl(container.getHsdsDomain(), owner, readers, writers, managers);
      Log.debugf(
        "HSDS bridge: synced domain=%s fingerprint=%s (kind=%s)",
        container.getHsdsDomain(),
        fingerprint,
        event.getKind()
      );
      return true;
    } catch (RuntimeException e) {
      Log.warnf(e, "HSDS bridge: setDomainAcl failed for domain=%s entityId=%s; queueing", container.getHsdsDomain(), event.getEntityId());
      enqueueRetry(event);
      return false;
    }
  }

  /** Drain and re-attempt every queued retry. Returns the count successfully drained. */
  public int drainRetries() {
    int drained = 0;
    int max;
    synchronized (retryQueue) {
      max = retryQueue.size();
    }
    for (int i = 0; i < max; i++) {
      RetryEntry entry;
      synchronized (retryQueue) {
        entry = retryQueue.pollFirst();
      }
      if (entry == null) break;
      if (syncOne(entry.event)) {
        drained++;
      }
    }
    return drained;
  }

  /** Cap the retry queue for tests. */
  void setRetryCapacity(int capacity) {
    if (capacity < 1) capacity = 1;
    this.retryCapacity = capacity;
  }

  /** Snapshot of the current retry queue (for tests / diagnostics). */
  List<RetryEntry> retrySnapshot() {
    synchronized (retryQueue) {
      return new ArrayList<>(retryQueue);
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private void enqueueRetry(PermissionsChangedEvent event) {
    synchronized (retryQueue) {
      while (retryQueue.size() >= retryCapacity) {
        retryQueue.pollFirst(); // drop oldest
      }
      retryQueue.addLast(new RetryEntry(event, System.currentTimeMillis()));
    }
  }

  private HsdsClient requireHsds() {
    if (hsdsClientInstance == null || hsdsClientInstance.isUnsatisfied()) {
      throw new IllegalStateException("HSDS client unavailable — shepard.hdf.enabled is off or misconfigured.");
    }
    return hsdsClientInstance.get();
  }

  private static Set<String> collectUsernames(List<User> users, List<User> extraFromGroups) {
    Set<String> out = new LinkedHashSet<>();
    if (users != null) {
      for (User u : users) {
        if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
          out.add(u.getUsername());
        }
      }
    }
    if (extraFromGroups != null) {
      for (User u : extraFromGroups) {
        if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
          out.add(u.getUsername());
        }
      }
    }
    return out;
  }

  /**
   * Flatten group memberships into the user list. HSDS doesn't have
   * a usergroup primitive at this granularity, so we expand groups
   * inline — same approach the shepard search-permissions path uses.
   */
  private static List<User> expandGroups(List<UserGroup> groups) {
    if (groups == null || groups.isEmpty()) return List.of();
    List<User> out = new ArrayList<>();
    for (UserGroup g : groups) {
      if (g == null) continue;
      List<User> members = g.getUsers();
      if (members == null) continue;
      for (User u : members) {
        if (u != null) out.add(u);
      }
    }
    return out;
  }

  /** In-memory retry record. */
  record RetryEntry(PermissionsChangedEvent event, long enqueuedAtMillis) {}
}
