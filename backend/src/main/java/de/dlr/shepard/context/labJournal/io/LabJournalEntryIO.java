package de.dlr.shepard.context.labJournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
   * J1d — application-level identifier (UUID v7) of the {@code LabJournalEntry}.
   *
   * <p>Additive field — existing clients that ignore unknown JSON keys are
   * unaffected. Appears on both the {@code /shepard/api/labJournalEntries}
   * compat surface and the {@code /v2/} surface.
   *
   * <p>Required for the history endpoint:
   * {@code GET /v2/lab-journal/{entryAppId}/history}.
   *
   * <p>May be {@code null} for entries created before L2a seeded appIds
   * (pre-existing rows without a backfill pass).
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, nullable = true, example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9")
  private String appId;

  /**
   * UI-DO-LABJOURNAL-V2 — UUID v7 of the parent DataObject.
   *
   * <p>Additive field — existing clients that ignore unknown JSON keys are unaffected.
   * The v2 collection endpoint ({@code GET /v2/collections/{appId}/lab-journal-entries})
   * needs this to let the frontend filter by DataObject appId without exposing the numeric
   * Neo4j id (which {@link de.dlr.shepard.v2.collection.io.CollectionV2IO} suppresses from
   * the wire via {@code @JsonIgnoreProperties({"id"})}).
   *
   * <p>May be {@code null} for entries whose DataObject was created before L2a seeded appIds
   * (no backfill pass has been run on those rows).
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, nullable = true, example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9")
  private String dataObjectAppId;

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
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, required = true, example = "MARKDOWN")
  private final String contentFormat = "MARKDOWN";

  public LabJournalEntryIO() {}

  public LabJournalEntryIO(LabJournalEntry labJournalEntry) {
    this.appId = labJournalEntry.getAppId();
    this.dataObjectId = labJournalEntry.getDataObject().getShepardId();
    this.dataObjectAppId = labJournalEntry.getDataObject().getAppId();
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
