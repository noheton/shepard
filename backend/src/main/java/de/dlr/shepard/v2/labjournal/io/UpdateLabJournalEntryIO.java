package de.dlr.shepard.v2.labjournal.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-LJE-ENTRY-V2-CRUD — request body for
 * {@code PUT /v2/lab-journal/{appId}}.
 */
@Data
@Schema(name = "UpdateLabJournalEntry")
public class UpdateLabJournalEntryIO {

  @NotNull
  @Schema(required = true, description = "New journal content (CommonMark markdown).")
  private String journalContent;
}
