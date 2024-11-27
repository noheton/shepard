package de.dlr.shepard.labJournal.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.labJournal.entities.LabJournal;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Schema(name = "LabJournal")
public class LabJournalIO {

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

  public LabJournalIO() {}

  public LabJournalIO(LabJournal labJournal) {
    this.dataObjectId = labJournal.getDataObject().getShepardId();
    this.journalContent = labJournal.getDescription();
    this.id = labJournal.getShepardId();
    this.createdAt = labJournal.getCreatedAt();
    this.createdBy = labJournal.getCreatedBy() != null ? labJournal.getCreatedBy().getUsername() : null;
    this.updatedAt = labJournal.getUpdatedAt();
    this.updatedBy = labJournal.getUpdatedBy() != null ? labJournal.getUpdatedBy().getUsername() : null;
  }
}
