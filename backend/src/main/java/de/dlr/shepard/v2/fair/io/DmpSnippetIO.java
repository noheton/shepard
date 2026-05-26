package de.dlr.shepard.v2.fair.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FAIR7 — JSON envelope returned by {@code GET /v2/collections/{appId}/dmp-snippet}
 * when the caller requests {@code Accept: application/json}.
 *
 * <p>The {@code snippet} field contains the same Markdown text that the
 * {@code text/markdown} response body returns, wrapped in a JSON object for
 * callers that need programmatic access to the individual fields or the
 * {@code missingFields} list.
 *
 * <p>{@code missingFields} is a list of human-readable field names that are
 * null / blank on the Collection and would improve the DMP statement if set.
 * Possible values: {@code "license"}, {@code "accessRights"}, {@code "orcid"},
 * {@code "description"}. An empty list means the DMP snippet is fully
 * populated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DmpSnippet")
public class DmpSnippetIO {

  @Schema(description = "appId of the Collection this snippet was generated from.", readOnly = true)
  private String collectionAppId;

  @Schema(description = "Name of the Collection.", readOnly = true)
  private String collectionName;

  @Schema(
    description = "Pre-filled Markdown DMP text block. Copy-paste into the data management plan form.",
    readOnly = true
  )
  private String snippet;

  @Schema(
    description = "FAIR fields that are null/blank and should be set to improve the DMP. " +
      "Possible values: 'license', 'accessRights', 'orcid', 'description'. " +
      "Empty list means all key fields are populated.",
    readOnly = true
  )
  private List<String> missingFields;
}
