package de.dlr.shepard.context.labJournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

  /**
   * APISIMP-LJE-DATAOBJECTID-NUMERIC: numeric Neo4j OGM id — kept for existing
   * callers; use {@code dataObjectAppId} (UUID v7) for new code.
   */
  @Schema(readOnly = true, required = true, deprecated = true,
      description = "DEPRECATED: numeric Neo4j OGM id of the parent DataObject. Use dataObjectAppId (UUID v7) instead.")
  @NotNull
  private long dataObjectId;

  /**
   * UUID v7 appId of the parent DataObject — the single stable cross-substrate
   * identity. Null only for entries whose DataObject predates the L2a appId
   * backfill.
   */
  @Schema(readOnly = true, nullable = true,
      description = "UUID v7 appId of the parent DataObject. Preferred over the deprecated dataObjectId.",
      example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9")
  private String dataObjectAppId;

  @NotNull
  private String journalContent;

  @Deprecated
  @JsonIgnore
  @Schema(
    readOnly = true,
    required = true,
    deprecated = true,
    description = "DEPRECATED — numeric Neo4j OGM node ID of this LabJournalEntry. Use appId (UUID v7) instead."
  )
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
