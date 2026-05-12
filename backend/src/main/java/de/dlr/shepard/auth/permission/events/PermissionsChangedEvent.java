package de.dlr.shepard.auth.permission.events;

import de.dlr.shepard.auth.permission.model.Permissions;
import java.util.Objects;

/**
 * A5b Phase 2 — CDI event fired by
 * {@link de.dlr.shepard.auth.permission.services.PermissionsService}
 * after every write path that mutates a {@link Permissions} row
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §6).
 *
 * <p>Audience: <em>any</em> downstream that has to mirror shepard's
 * permission graph into a foreign permission system. In this slice
 * the sole consumer is
 * {@link de.dlr.shepard.data.hdf.permissions.HdfPermissionBridge}
 * (HSDS domain ACL sync). Future systems (e.g. an external
 * Keycloak group sync, an OPA bundle rebuild) plug in the same way
 * with their own {@code @Observes PermissionsChangedEvent} listeners.
 *
 * <p><strong>Best-effort semantics.</strong> Event firing is
 * decoupled from the shepard write — listeners <em>never</em> block
 * the user's request and never throw back into the firing path. The
 * shepard write is the source of truth; downstream sync is
 * eventually consistent. See {@code aidocs/35 §6} for the full
 * sync-direction contract.
 *
 * <p>The payload is a snapshot of the salient bits at fire time —
 * we don't carry the full {@link Permissions} object to avoid
 * serialisation surprises and to keep listeners decoupled from the
 * write transaction. Listeners that need more data re-fetch via
 * the entityId.
 */
public final class PermissionsChangedEvent {

  /** Neo4j ID of the entity whose Permissions row was mutated. */
  private final long entityId;

  /**
   * Coarse-grained entity kind ("HdfContainer", "FileContainer",
   * "Collection", "UserGroup", …). Listeners filter on this rather
   * than instanceof-introspecting an opaque object — keeps the bridge
   * cheap on entity kinds it doesn't care about.
   */
  private final String entityKind;

  /**
   * Application-level identifier of the entity if it has one
   * ({@link de.dlr.shepard.common.identifier.HasAppId}); {@code null}
   * for entities pre-dating the L2a appId addition. Listeners that
   * key off appId (e.g. HSDS domain path) should fall back to
   * entityId when this is {@code null}.
   */
  private final String entityAppId;

  /**
   * The kind of mutation that fired this event. Mostly informational
   * — listeners typically re-derive the ACL from scratch rather than
   * applying a diff.
   */
  public enum Kind {
    /** A new Permissions row was created (e.g. container created). */
    CREATED,
    /** An existing Permissions row was mutated (grant / revoke / set). */
    UPDATED,
    /** A Permissions row was deleted (e.g. container hard-deleted). */
    DELETED
  }

  private final Kind kind;

  public PermissionsChangedEvent(long entityId, String entityKind, String entityAppId, Kind kind) {
    this.entityId = entityId;
    this.entityKind = entityKind;
    this.entityAppId = entityAppId;
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  public long getEntityId() {
    return entityId;
  }

  public String getEntityKind() {
    return entityKind;
  }

  public String getEntityAppId() {
    return entityAppId;
  }

  public Kind getKind() {
    return kind;
  }

  @Override
  public String toString() {
    return (
      "PermissionsChangedEvent{entityId=" +
      entityId +
      ", entityKind='" +
      entityKind +
      "', entityAppId='" +
      entityAppId +
      "', kind=" +
      kind +
      '}'
    );
  }
}
