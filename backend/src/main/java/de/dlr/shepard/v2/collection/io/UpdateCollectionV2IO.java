package de.dlr.shepard.v2.collection.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.validation.NoDelimiterInMapKeys;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-COLLECTION-CREATE-LONG-INPUT — OpenAPI schema reference for
 * {@code PATCH /v2/collections/{appId}} (RFC 7396 JSON Merge Patch).
 *
 * <p>All fields are optional (RFC 7396: absent = unchanged, null = clear).
 * Used only as the {@code @Schema(implementation = ...)} annotation target on
 * the PATCH {@code @RequestBody}; the actual parameter type remains
 * {@code JsonNode} so Jackson can distinguish absent from null.
 *
 * <p>{@code defaultFileContainerAppId} is writable here (unlike the shared
 * {@link de.dlr.shepard.context.collection.io.CollectionIO} which marks it
 * {@code readOnly = true} to preserve v1 byte-fidelity).
 * {@code defaultFileContainerId} is absent — v2 callers use the appId form.
 */
@Data
@NoArgsConstructor
@Schema(
  name = "UpdateCollectionV2",
  description = "Partial update body for PATCH /v2/collections/{appId} (RFC 7396). " +
    "All fields optional; absent = unchanged, null = clear."
)
public class UpdateCollectionV2IO {

  @Schema(nullable = true, example = "LUMEN Hotfire Campaign 2024")
  private String name;

  @Schema(nullable = true)
  private String description;

  @NoDelimiterInMapKeys
  private Map<String, String> attributes;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    enumeration = {
      "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED",
      "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED", "CONCESSION_PENDING"
    }
  )
  private String status;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String license;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"OPEN", "RESTRICTED", "CLOSED", "EMBARGOED"})
  private String accessRights;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
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
   * AppId (UUID v7) of the default FileContainer. Null clears the default;
   * absent leaves it unchanged (RFC 7396 semantics).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, description = "AppId (UUID v7) of the default FileContainer. Null = clear.")
  private String defaultFileContainerAppId;
}
