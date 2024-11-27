package de.dlr.shepard.labJournal.io;

import de.dlr.shepard.labJournal.entities.LabJournal;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@Schema(name = "LabJournal")
public class LabJournalIO {

  public LabJournalIO(LabJournal labJournal) {
    this.dataObjectId = labJournal.getDataObject().getShepardId();
    this.journalContent = labJournal.getDescription();
    this.labJournalId = labJournal.getShepardId();
  }

  @NotNull
  private long dataObjectId;

  @NotNull
  private String journalContent;

  @NotNull
  private long labJournalId;
}
