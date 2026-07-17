package de.dlr.shepard.v2.labjournal.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import java.time.Instant;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-LABJOURNAL-NUMID-DATE — v2-clean response shape for lab journal entries.
 *
 * <p>Differences from the shared {@code LabJournalEntryIO}:
 * <ul>
 *   <li>No {@code dataObjectId} (numeric Neo4j id — was an upstream-compat field).
 *       Use {@code dataObjectAppId} (UUID v7) instead.</li>
 *   <li>No numeric {@code id}. Use {@code appId} (UUID v7) instead.</li>
 *   <li>{@code createdAt}/{@code updatedAt} are {@code Instant} (ISO-8601 UTC string
 *       on the wire), not {@code java.util.Date}.</li>
 * </ul>
 *
 * <p>The shared {@link de.dlr.shepard.context.labJournal.io.LabJournalEntryIO}
 * remains on the frozen v1 {@code /shepard/api/labJournalEntries} surface unchanged.
 */
@Data
@Schema(name = "LabJournalEntryV2")
public class LabJournalEntryV2IO {

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, nullable = true, example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9")
  private String appId;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "UUID v7 appId of the parent DataObject.",
    example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9"
  )
  private String dataObjectAppId;

  @Schema(required = true)
  private String journalContent;

  @Schema(readOnly = true, format = "date-time", example = "2024-08-15T11:18:44.632Z")
  private Instant createdAt;

  @Schema(readOnly = true)
  private String createdBy;

  @Schema(readOnly = true, nullable = true, format = "date-time", example = "2024-08-15T11:18:44.632Z")
  private Instant updatedAt;

  @Schema(readOnly = true, nullable = true)
  private String updatedBy;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, required = true, example = "MARKDOWN")
  private final String contentFormat = "MARKDOWN";

  public LabJournalEntryV2IO() {}

  public LabJournalEntryV2IO(LabJournalEntry entry) {
    this.appId = entry.getAppId();
    this.dataObjectAppId = entry.getDataObject() != null ? entry.getDataObject().getAppId() : null;
    this.journalContent = entry.getContent();
    this.createdAt = entry.getCreatedAt() != null ? entry.getCreatedAt().toInstant() : null;
    this.createdBy = entry.getCreatedBy() != null
        ? DisplayNameResolver.effectiveDisplayName(entry.getCreatedBy()) : null;
    this.updatedAt = entry.getUpdatedAt() != null ? entry.getUpdatedAt().toInstant() : null;
    this.updatedBy = entry.getUpdatedBy() != null
        ? DisplayNameResolver.effectiveDisplayName(entry.getUpdatedBy()) : null;
  }
}
