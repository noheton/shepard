package de.dlr.shepard.context.labJournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Schema(name = "LabJournalEntry")
public class LabJournalEntryIO {

  @Schema(readOnly = true, required = true)
  @NotNull
  private long dataObjectId;

  @NotNull
  private String journalContent;

  @Schema(readOnly = true, required = true)
  private Long id;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, required = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @Schema(readOnly = true, required = true)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    readOnly = true,
    nullable = true,
    required = true,
    format = "date-time",
    example = "2024-08-15T11:18:44.632+00:00"
  )
  private Date updatedAt;

  @Schema(readOnly = true, nullable = true, required = true)
  private String updatedBy;

  /**
   * J1a — fixed constant indicating that all lab journal entries are interpreted
   * as CommonMark + GFM markdown by the render endpoint
   * ({@code GET /v2/lab-journal/{appId}/render}).
   *
   * <p>The format is implicit (always CommonMark from J1a onward); this field
   * tells clients what they should expect from the render endpoint without
   * requiring them to check a separate capability endpoint. The value is
   * {@code "MARKDOWN"} for all entries, including pre-J1a plain-text entries
   * (which CommonMark passes through unchanged as {@code <p>} elements).
   *
   * <p>This field is additive — existing clients that ignore unknown JSON fields
   * are unaffected. It appears on both the {@code /shepard/api/labJournalEntries}
   * compat surface and the new {@code /v2/} surface.
   */
  @Schema(readOnly = true, required = true, example = "MARKDOWN")
  private final String contentFormat = "MARKDOWN";

  public LabJournalEntryIO() {}

  public LabJournalEntryIO(LabJournalEntry labJournalEntry) {
    this.dataObjectId = labJournalEntry.getDataObject().getShepardId();
    this.journalContent = labJournalEntry.getContent();
    this.id = labJournalEntry.getId();
    this.createdAt = labJournalEntry.getCreatedAt();
    this.createdBy = labJournalEntry.getCreatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(labJournalEntry.getCreatedBy())
      : null;
    this.updatedAt = labJournalEntry.getUpdatedAt();
    this.updatedBy = labJournalEntry.getUpdatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(labJournalEntry.getUpdatedBy())
      : null;
  }
}
