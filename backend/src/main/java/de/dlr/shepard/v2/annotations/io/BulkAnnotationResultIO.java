package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    name = "BulkAnnotationResultV2",
    description =
        "SEMANTIC-ANNOTATE-BULK-REST-1 — HTTP 207 response body for POST /v2/annotations/bulk.")
public class BulkAnnotationResultIO {

  @Schema(description = "Number of annotation specs that were successfully created.")
  private int created;

  @Schema(description = "Number of annotation specs that failed (validation, permission, etc.).")
  private int failed;

  @Schema(description = "Per-spec outcomes, in the same order as the request list.")
  private List<BulkAnnotationResultItemIO> results;
}
