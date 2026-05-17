package de.dlr.shepard.v2.labjournal.io;

import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntryRevision;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * J1d — JSON response shape for one element in the
 * {@code GET /v2/lab-journal/{entryAppId}/history} array.
 *
 * <p>Each element represents a point-in-time snapshot of a
 * {@code LabJournalEntry}'s content, captured immediately before an update
 * overwrote it. Revisions are ordered newest-first by the endpoint.
 */
@Data
@NoArgsConstructor
@Schema(name = "LabJournalRevision")
public class LabJournalRevisionIO {

  /** Application-level identifier of the revision node. */
  @Schema(readOnly = true)
  private String appId;

  /**
   * The content of the entry as it existed immediately before the edit that
   * produced this revision.
   */
  @Schema(description = "Content of the entry as it existed before the edit.")
  private String content;

  /** Timestamp at which the revision was captured (= when the update was applied). */
  @Schema(readOnly = true, description = "When the revision was captured.")
  private Date revisedAt;

  /**
   * Display name of the user who performed the edit that produced this revision
   * (i.e. the author of the update, not the original author of the entry).
   */
  @Schema(readOnly = true, description = "Display name of the user who made the edit.")
  private String revisedBy;

  /**
   * 1-based, monotonically increasing revision number within the entry.
   * Revision 1 is the snapshot from the first edit.
   */
  @Schema(readOnly = true, description = "1-based revision counter (1 = first edit).")
  private int revisionNumber;

  /** Constructs an IO from a {@link LabJournalEntryRevision} entity. */
  public LabJournalRevisionIO(LabJournalEntryRevision revision) {
    this.appId = revision.getAppId();
    this.content = revision.getContent();
    this.revisedAt = revision.getCreatedAt();
    this.revisedBy =
      revision.getCreatedBy() != null ? DisplayNameResolver.effectiveDisplayName(revision.getCreatedBy()) : null;
    this.revisionNumber = revision.getRevisionNumber();
  }
}
