package de.dlr.shepard.context.snapshot.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

/**
 * V2b — one entry in a {@link Snapshot}'s manifest, pinning a single
 * {@link de.dlr.shepard.context.version.entities.VersionableEntity} to
 * the {@code revision} it held at snapshot creation time.
 *
 * <p>{@code SnapshotEntry} nodes are append-only: once created they are
 * never mutated. Deleting the parent {@link Snapshot} soft-deletes the
 * entry rows via the service layer (no cascade in Neo4j OGM).
 *
 * <p>Fields inherited from {@link AbstractEntity}:
 * <ul>
 *   <li>{@code appId} — UUID v7, minted on save by {@code GenericDAO}.</li>
 *   <li>{@code createdAt}, {@code createdBy}, {@code deleted} — standard.</li>
 * </ul>
 *
 * <p>Cross-references: {@code aidocs/41} §4.1; {@code aidocs/16} V2b row.
 */
@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class SnapshotEntry extends AbstractEntity {

  /**
   * The application-level identifier ({@code appId}) of the
   * {@link de.dlr.shepard.context.version.entities.VersionableEntity}
   * captured by this entry. Stored as a scalar property — not a relationship
   * — so the manifest can be read with a single Cypher query even if the
   * target entity is soft-deleted.
   */
  @Property
  private String entityAppId;

  /**
   * The {@code revision} counter value the entity held at snapshot time.
   * Reads via this snapshot should resolve the entity at exactly this revision.
   */
  @Property
  private long revision;

  /**
   * Back-reference to the owning {@link Snapshot}. The graph edge runs
   * {@code (entry)-[:ENTRY_OF]->(snapshot)}; the field direction is therefore
   * OUTGOING from this node's perspective.
   */
  @Relationship(type = "ENTRY_OF", direction = Direction.OUTGOING)
  private Snapshot snapshot;
}
