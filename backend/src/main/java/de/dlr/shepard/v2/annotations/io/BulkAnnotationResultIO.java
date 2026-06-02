package de.dlr.shepard.v2.annotations.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — one element in the response array of
 * {@code POST /v2/annotations/bulk}.
 *
 * <p>Each element corresponds to the input row at the same index.
 * <ul>
 *   <li>Success: {@code ok=true}, {@code appId} is the minted annotation UUID,
 *       {@code error} is {@code null}.</li>
 *   <li>Failure: {@code ok=false}, {@code appId} is {@code null},
 *       {@code error} is a short message.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Schema(name = "BulkAnnotationResult",
    description = "SEMANTIC-ANNOTATE-BULK-REST-1 — result for one row in a POST /v2/annotations/bulk call.")
public class BulkAnnotationResultIO {

  @Schema(required = true, description = "true when the row was created successfully; false when it failed.")
  private boolean ok;

  @Schema(nullable = true,
      description = "UUID v7 of the minted `:SemanticAnnotation` node. Null when ok=false.")
  private String appId;

  @Schema(nullable = true,
      description = "appId of the subject entity from the input row. Null when the input row omitted it.")
  private String subjectAppId;

  @Schema(nullable = true,
      description = "Short error message. Null when ok=true.")
  private String error;

  /** Convenience constructor for a successful row. */
  public BulkAnnotationResultIO(String annotationAppId, String subjectAppId) {
    this.ok = true;
    this.appId = annotationAppId;
    this.subjectAppId = subjectAppId;
    this.error = null;
  }

  /** Convenience constructor for a failed row. */
  public static BulkAnnotationResultIO failure(String subjectAppId, String errorMessage) {
    BulkAnnotationResultIO r = new BulkAnnotationResultIO();
    r.ok = false;
    r.appId = null;
    r.subjectAppId = subjectAppId;
    r.error = errorMessage;
    return r;
  }
}
