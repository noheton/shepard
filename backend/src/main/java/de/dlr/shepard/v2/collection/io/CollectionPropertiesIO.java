package de.dlr.shepard.v2.collection.io;

import de.dlr.shepard.context.collection.entities.CollectionProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for the {@code /v2/collections/{appId}/properties}
 * GET response. Mirrors {@link CollectionProperties}'s shipped
 * v1 fields per {@code aidocs/58 §5}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CollectionProperties")
public class CollectionPropertiesIO {

  @Schema(required = true, description = "Application-level identifier (UUID v7).")
  private String appId;

  @Schema(required = true, description = "Whether this Collection appears in the /v2/webdav mount surface.")
  private boolean webdavVisible;

  @Schema(required = false, nullable = true, description = "Optional Collection-default ontology IRI.")
  private String defaultOntologyUri;

  @Schema(required = false, nullable = true, description = "Free-form UI defaults JSON (opaque to the backend).")
  private String uiDefaultsJson;

  @Schema(
    required = true,
    description = "When false, this Collection is excluded from the Helmholtz Unhide feed (/v2/unhide/feed.jsonld). " +
    "Default true (opt-out rather than opt-in). UH1d."
  )
  private boolean publishToHelmholtzKG;

  public static CollectionPropertiesIO from(CollectionProperties p) {
    var io = new CollectionPropertiesIO(p.getAppId(), p.isWebdavVisible(), p.getDefaultOntologyUri(), p.getUiDefaultsJson(), p.isPublishToHelmholtzKG());
    return io;
  }
}
