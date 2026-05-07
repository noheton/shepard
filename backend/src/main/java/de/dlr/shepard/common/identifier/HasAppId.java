package de.dlr.shepard.common.identifier;

/**
 * Marker interface for OGM-mapped node entities that carry an application-level
 * identifier (UUID v7) in addition to the legacy Neo4j {@code id()} primary key.
 *
 * <p>Phase L2a of the Neo4j-ID migration adds {@code appId} to every protected
 * node-entity on the write side. The {@code appId} is set on save by
 * {@code GenericDAO#createOrUpdate} via {@link AppIdGenerator#next()} when the
 * field is still {@code null}; existing rows pre-dating this phase keep
 * {@code appId == null} until L2b's backfill migration populates them. The read
 * path is unchanged in L2a — the canonical lookup is still Neo4j's {@code id()}.
 *
 * <p>Public API exposure of {@code appId} is L2d's territory; do not surface it
 * on IO/DTO types yet.
 */
public interface HasAppId {
  /**
   * @return the entity's application-level UUID v7 identifier, or {@code null}
   *         if the entity has not yet been persisted (or was created before
   *         L2a's backfill).
   */
  String getAppId();

  /**
   * Set the entity's application-level UUID v7 identifier.
   *
   * @param appId the new appId; should never be reassigned once persisted.
   */
  void setAppId(String appId);
}
