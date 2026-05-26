package de.dlr.shepard.v2.dataobject.io;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-BATCH-01 — one item in a {@code POST /v2/data-objects/batch} request.
 *
 * <p>Each item describes a single {@code DataObject} to be created.
 * {@code collectionAppId} and {@code name} are required; all other fields are
 * optional and mirror the single-create {@link
 * de.dlr.shepard.context.collection.io.DataObjectIO} body shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
  name = "DataObjectBatchCreateItem",
  description =
    "One DataObject to create as part of a batch. " +
    "`collectionAppId` and `name` are required; all other fields are optional."
)
public class DataObjectBatchCreateItemIO {

  @Schema(
    required = true,
    description = "The appId (UUID v7) of the Collection in which to create this DataObject."
  )
  private String collectionAppId;

  @Schema(required = true, description = "Non-blank display name of the DataObject.")
  private String name;

  @Schema(nullable = true, description = "Optional CommonMark / GFM description.")
  private String description;

  @Schema(
    nullable = true,
    description =
      "Optional appId (UUID v7) of an existing DataObject to set as the parent " +
      "(hierarchical containment, not provenance). Null if top-level."
  )
  private String parentAppId;

  @Schema(
    nullable = true,
    description =
      "Optional string-to-string attribute map. Keys and values are free-form strings; " +
      "they are written as Neo4j node properties on the created DataObject."
  )
  private Map<String, String> attributes;

  @Schema(
    nullable = true,
    description =
      "Optional initial status. Suggested values: DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED. " +
      "Any free-form string is accepted; the server does not enforce a state machine."
  )
  private String status;
}
