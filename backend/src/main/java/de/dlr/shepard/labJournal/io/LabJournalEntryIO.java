package de.dlr.shepard.labJournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.labJournal.entities.LabJournalEntry;
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

  public LabJournalEntryIO() {}

  public LabJournalEntryIO(LabJournalEntry labJournalEntry) {
    this.dataObjectId = labJournalEntry.getDataObject().getShepardId();
    this.journalContent = labJournalEntry.getContent();
    this.id = labJournalEntry.getId();
    this.createdAt = labJournalEntry.getCreatedAt();
    this.createdBy = labJournalEntry.getCreatedBy() != null ? labJournalEntry.getCreatedBy().getUsername() : null;
    this.updatedAt = labJournalEntry.getUpdatedAt();
    this.updatedBy = labJournalEntry.getUpdatedBy() != null ? labJournalEntry.getUpdatedBy().getUsername() : null;
  }
}
