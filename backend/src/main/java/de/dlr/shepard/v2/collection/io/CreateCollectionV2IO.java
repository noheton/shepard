package de.dlr.shepard.v2.collection.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.validation.NoDelimiterInMapKeys;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-COLLECTION-CREATE-LONG-INPUT — v2-specific input body for
 * {@code POST /v2/collections}.
 *
 * <p>Replaces {@link de.dlr.shepard.context.collection.io.CollectionIO} as
 * the POST input schema so that:
 * <ul>
 *   <li>{@code defaultFileContainerId} (raw Neo4j Long) is absent from the
 *       OpenAPI input schema — v2 callers must not use it.</li>
 *   <li>{@code defaultFileContainerAppId} (UUID v7 string) is writable — v2
 *       callers supply this to set the Collection's default FileContainer.</li>
 * </ul>
 *
 * <p>The REST handler translates this record into a
 * {@link de.dlr.shepard.context.collection.io.CollectionIO} before handing
 * it to {@link de.dlr.shepard.context.collection.services.CollectionService},
 * which resolves the appId via
 * {@link de.dlr.shepard.data.file.services.FileContainerService#getContainerByAppId}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateCollectionV2", description = "Input body for POST /v2/collections.")
public class CreateCollectionV2IO {

  @NotBlank
  @Schema(required = true, example = "LUMEN Hotfire Campaign 2024")
  private String name;

  @Schema(nullable = true, example = "Post-hotfire analysis of turbopump vibration anomaly at t=8s (TR-004)")
  private String description;

  @NoDelimiterInMapKeys
  private Map<String, String> attributes = new HashMap<>();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    enumeration = {
      "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED",
      "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED", "CONCESSION_PENDING"
    },
    example = "DRAFT"
  )
  private String status;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String license;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"OPEN", "RESTRICTED", "CLOSED", "EMBARGOED"})
  private String accessRights;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, description = "ISO-8601 date (e.g. '2027-12-31') after which the embargo lifts.")
  private String embargoEndDate;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String heroImageUrl;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String importedFrom;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"HASH_ONLY", "BODY_REDACTED", "BODY_RAW"})
  private String promptLogMode;

  /**
   * AppId (UUID v7) of the {@code :FileContainer} to set as this Collection's
   * default. Null (or absent) leaves no default set. Resolved server-side via
   * {@link de.dlr.shepard.data.file.services.FileContainerService#getContainerByAppId}.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, description = "AppId (UUID v7) of the default FileContainer. Null = no default.")
  private String defaultFileContainerAppId;
}
