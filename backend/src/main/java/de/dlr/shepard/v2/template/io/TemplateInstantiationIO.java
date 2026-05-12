package de.dlr.shepard.v2.template.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response shape for {@code POST
 * /v2/collections/{appId}/from-template/{templateAppId}}. The
 * server stamps the {@code :USES_TEMPLATE} edge and returns the
 * template body so the client (frontend / CLI) can interpret the
 * JSON DSL and mint entities.
 *
 * <p>v1 design choice (`aidocs/54 §5`): server-side body
 * interpretation is deferred to a later slice. The "from-template"
 * endpoint records the cite + hands back the recipe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TemplateInstantiation")
public class TemplateInstantiationIO {

  @Schema(required = true, description = "Collection that cited the template.")
  private String collectionAppId;

  @Schema(required = true, description = "Template's stable appId.")
  private String templateAppId;

  @Schema(required = true, description = "Template kind — guides client interpretation.")
  private String templateKind;

  @Schema(required = true, description = "Template version at instantiation time (copy-on-write history is preserved).")
  private Integer templateVersion;

  @Schema(required = true, description = "Recipe body. JSON DSL per aidocs/54 §7 — client interprets.")
  private String body;

  @Schema(required = true, description = "Whether the :USES_TEMPLATE edge was created on this call. False on idempotent re-stamp (the edge already existed).")
  private boolean edgeCreated;
}
