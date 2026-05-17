package de.dlr.shepard.v2.collection.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Partial-update body for the {@code PATCH
 * /v2/collections/{appId}/properties} endpoint. Every field is
 * nullable — only supplied fields apply. {@code appId} is
 * intentionally omitted (the path parameter is the identity).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PatchCollectionProperties")
public class PatchCollectionPropertiesIO {

  @Schema(required = false, nullable = true, description = "If set, flips the WebDAV-mount visibility for this Collection.")
  private Boolean webdavVisible;

  @Schema(required = false, nullable = true, description = "If set, replaces the Collection-default ontology IRI.")
  private String defaultOntologyUri;

  @Schema(
    required = false,
    nullable = true,
    description = "If set, replaces the free-form UI defaults JSON blob (opaque to the backend)."
  )
  private String uiDefaultsJson;

  @Schema(
    required = false,
    nullable = true,
    description = "If set, flips whether this Collection appears in the Helmholtz Unhide feed. " +
    "null = leave unchanged; false = opt out of the feed; true = opt back in (the default)."
  )
  private Boolean publishToHelmholtzKG;
}
