package de.dlr.shepard.v2.annotations.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BulkAnnotationResultItemV2", description = "Outcome for one annotation spec in a bulk-create response.")
public class BulkAnnotationResultItemIO {

  @Schema(description = "Zero-based index of the spec in the request list.")
  private int index;

  @Schema(description = "\"created\" on success, \"error\" on failure.")
  private String status;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "AppId of the newly created annotation (success only).")
  private String appId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "AppId of the subject entity this annotation targets (success only).")
  private String subjectAppId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Machine-readable error code (error only).")
  private String errorCode;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Human-readable error detail (error only).")
  private String errorMessage;

  public static BulkAnnotationResultItemIO success(int index, String appId, String subjectAppId) {
    return new BulkAnnotationResultItemIO(index, "created", appId, subjectAppId, null, null);
  }

  public static BulkAnnotationResultItemIO error(
      int index, String errorCode, String errorMessage) {
    return new BulkAnnotationResultItemIO(index, "error", null, null, errorCode, errorMessage);
  }
}
