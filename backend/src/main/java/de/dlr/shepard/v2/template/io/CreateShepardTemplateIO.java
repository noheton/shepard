package de.dlr.shepard.v2.template.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * POST body for {@code POST /v2/templates}.
 *
 * <p>{@code appId} is server-minted; {@code version} starts at 1.
 * {@code createdBy} comes from the authenticated principal — never
 * from the body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateShepardTemplate")
public class CreateShepardTemplateIO {

  @Schema(required = true, description = "Human-readable template name.")
  private String name;

  @Schema(required = true, description = "Template kind, e.g. DATAOBJECT_RECIPE / COLLECTION_RECIPE / EXPERIMENT_RECIPE.")
  private String templateKind;

  @Schema(required = true, description = "Recipe body. JSON DSL per aidocs/54 §7.")
  private String body;

  @Schema(required = false, nullable = true, description = "Markdown-flavoured human description.")
  private String description;

  @Schema(required = false, nullable = true, description = "Author-supplied tags for picker filtering.")
  private List<String> tags;
}
