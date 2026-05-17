package de.dlr.shepard.context.snapshot.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import de.dlr.shepard.context.collection.entities.Collection;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

/**
 * V2b — a point-in-time logical snapshot of a {@link Collection} subtree.
 *
 * <p>A {@code Snapshot} records, for every {@link de.dlr.shepard.context.version.entities.VersionableEntity}
 * reachable from a {@link Collection} at snapshot time, the entity's
 * {@code appId} and its current {@code revision} counter. Subsequent writes to
 * live entities do not affect the snapshot; the {@link SnapshotEntry} rows
 * remain fixed.
 *
 * <p>The snapshot itself is immutable after creation — no update path exists.
 *
 * <p>Fields inherited from {@link AbstractEntity}:
 * <ul>
 *   <li>{@code appId} — UUID v7, minted on save by {@code GenericDAO}.</li>
 *   <li>{@code createdAt} — standard OGM-managed {@code Date}.</li>
 *   <li>{@code createdBy} — {@code :CREATED_BY} edge to a {@code :User} node.</li>
 *   <li>{@code deleted} — soft-delete flag (set on {@code DELETE} endpoint).</li>
 * </ul>
 *
 * <p>{@code snapshotCapturedAt} and {@code snapshotCreatedByUsername} capture
 * the frozen <em>snapshot-time</em> who/when as scalar properties — separately
 * from the AbstractEntity fields — so they survive even if the User node or
 * the OGM session is unavailable during a read.
 *
 * <p>Cross-references: {@code aidocs/41} §4 entity model; {@code aidocs/16}
 * V2b backlog row.
 */
@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Snapshot extends AbstractEntity {

  /** User-visible label, e.g. {@code "v1.0 — campaign close"}. Required. */
  @Property
  private String name;

  /** Optional free-text description of what the snapshot captures. */
  @Property
  private String description;

  /**
   * Epoch milliseconds at which the snapshot was captured — frozen at creation
   * time. Stored as a primitive {@code long} so it survives without a
   * Neo4j date converter.
   *
   * <p>Named {@code snapshotCapturedAt} (not {@code capturedAt}) to avoid any
   * collision with future AbstractEntity additions.
   */
  @Property
  private long snapshotCapturedAtMs;

  /**
   * Display name of the caller who triggered the snapshot — frozen at creation
   * time as a scalar property, separate from the {@code :CREATED_BY}
   * relationship, so the identity is readable even if the User entity changes.
   *
   * <p>Named {@code snapshotCreatedByUsername} to avoid collision with
   * AbstractEntity's {@code createdBy} relationship field.
   */
  @Property
  private String snapshotCreatedByUsername;

  /**
   * The root {@link Collection} that was walked when this snapshot was created.
   * The traversal follows {@code (c:Collection)-[*0..15]->(e:VersionableEntity)}.
   */
  @Relationship(type = "SNAPSHOT_OF")
  private Collection collection;

  /**
   * Count of {@link SnapshotEntry} rows captured for this snapshot. Denormalised
   * at creation time so the metadata endpoint can return it without loading
   * the full entry list.
   */
  @Property
  private int entryCount;
}
