package de.dlr.shepard.v2.annotations.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — per-row result in a bulk annotation response.
 *
 * <p>One instance per entry in the {@code POST /v2/annotations/bulk} request array.
 * Failed rows ({@link #ok} = {@code false}) set {@link #error} and leave {@link #appId} null;
 * they do NOT abort the rest of the batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BulkAnnotationItemResult", description = "Per-row result from POST /v2/annotations/bulk.")
public class BulkAnnotationItemResultIO {

  @Schema(required = true, description = "true if the annotation was created; false if it failed.")
  private boolean ok;

  @Schema(nullable = true, description = "appId (UUID v7) of the newly created annotation. Null on failure.")
  private String appId;

  @Schema(nullable = true, description = "subjectAppId from the request row (echoed back for correlation).")
  private String subjectAppId;

  @Schema(nullable = true, description = "Error message when ok=false. Null on success.")
  private String error;
}
