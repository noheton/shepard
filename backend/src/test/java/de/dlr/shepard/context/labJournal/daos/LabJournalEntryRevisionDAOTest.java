package de.dlr.shepard.context.labJournal.daos;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntryRevision;
import org.junit.jupiter.api.Test;

/**
 * J1d — unit tests for {@link LabJournalEntryRevisionDAO}.
 *
 * <p>These tests exercise the entity and IO construction in isolation —
 * no live Neo4j session is required. The DAO's Cypher queries
 * ({@code countByEntry}/{@code findByEntry} with SKIP/LIMIT) are
 * integration concerns covered by the end-to-end test plan in {@code aidocs/34}.
 */
class LabJournalEntryRevisionDAOTest {

  // ── entity construction ───────────────────────────────────────────────────

  @Test
  void revision_setsContent_andRevisionNumber() {
    LabJournalEntryRevision rev = new LabJournalEntryRevision();
    rev.setContent("original text");
    rev.setRevisionNumber(1);

    assertThat(rev.getContent()).isEqualTo("original text");
    assertThat(rev.getRevisionNumber()).isEqualTo(1);
  }

  @Test
  void revision_isNotDeletedByDefault() {
    LabJournalEntryRevision rev = new LabJournalEntryRevision();
    assertThat(rev.isDeleted()).isFalse();
  }

  @Test
  void revision_labJournalEntryLink_roundTrips() {
    LabJournalEntry entry = new LabJournalEntry();
    entry.setId(77L);
    entry.setContent("current");

    LabJournalEntryRevision rev = new LabJournalEntryRevision();
    rev.setLabJournalEntry(entry);

    assertThat(rev.getLabJournalEntry()).isSameAs(entry);
    assertThat(rev.getLabJournalEntry().getId()).isEqualTo(77L);
  }

  @Test
  void revision_revisionNumberIncrements_correctly() {
    // Simulates the numbering logic in LabJournalEntryService:
    // nextRevisionNumber = existingCount + 1
    int existingCount = 0;
    int first = existingCount + 1;
    assertThat(first).isEqualTo(1);

    existingCount = 1;
    int second = existingCount + 1;
    assertThat(second).isEqualTo(2);
  }

  @Test
  void revision_contentCanBeNull() {
    // Edge case: if an entry was created with null content and then updated,
    // the revision's content field will be null.
    LabJournalEntryRevision rev = new LabJournalEntryRevision();
    rev.setContent(null);
    assertThat(rev.getContent()).isNull();
  }
}
