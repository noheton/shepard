package de.dlr.shepard.labJournal.io;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@Schema(name = "LabJournal")
public class LabJournalIO {

  @NotNull
  private long dataObjectId;

  @NotNull
  private String journalContent;
}
