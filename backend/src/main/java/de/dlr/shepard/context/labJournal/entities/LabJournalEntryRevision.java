package de.dlr.shepard.context.labJournal.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

/**
 * J1d — a point-in-time snapshot of a {@link LabJournalEntry}'s content,
 * captured immediately before an update overwrites it.
 *
 * <p>Revisions are append-only: once created they are never mutated or
 * hard-deleted. The {@code deleted} flag inherited from {@link AbstractEntity}
 * is not used for revisions; soft-delete semantics live on the parent entry.
 *
 * <p>The {@code has_lab_journal_revision} edge runs
 * {@code LabJournalEntry → LabJournalEntryRevision}, matching the direction
 * used by the DAO's Cypher query. The field is declared {@code INCOMING} here
 * so that when OGM saves a revision it persists the edge automatically,
 * mirroring the pattern used by {@code LabJournalEntry.dataObject} (INCOMING
 * from {@code DataObject}).
 *
 * <p>{@code revisionNumber} is 1-based and monotonically increasing within a
 * single entry. The first revision (from the first edit) gets number 1.
 */
@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LabJournalEntryRevision extends AbstractEntity {

  /** The full content of the entry as it existed immediately before the update. */
  private String content;

  /**
   * 1-based revision counter. Computed as the current number of revisions on
   * the entry plus one, so the first edit produces revision 1.
   */
  private int revisionNumber;

  /**
   * Back-reference to the parent {@link LabJournalEntry}. The edge in the
   * graph runs {@code (entry)-[:has_lab_journal_revision]->(revision)};
   * the field direction is therefore INCOMING from this node's perspective.
   */
  @Relationship(type = "has_lab_journal_revision", direction = Direction.INCOMING)
  private LabJournalEntry labJournalEntry;
}
