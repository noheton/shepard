package de.dlr.shepard.v2.template.io;

import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.template.entities.ShepardTemplate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code /v2/templates/...} GET responses, per
 * {@code aidocs/54 §5}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ShepardTemplate")
public class ShepardTemplateIO {

  @Schema(required = true, description = "Application-level identifier (UUID v7).")
  private String appId;

  @Schema(required = true, description = "Human-readable template name.")
  private String name;

  @Schema(required = true, description = "Template kind, e.g. DATAOBJECT_RECIPE / COLLECTION_RECIPE / EXPERIMENT_RECIPE.")
  private String templateKind;

  @Schema(required = true, description = "Copy-on-write version number; latest non-retired row has the highest value for the name.")
  private Integer version;

  /**
   * SHAPES-V-PREFILL-3-EXTRACT-SHACL -- Template authors may embed a
   * SHACL shape graph (Turtle) under the optional JSON field
   * {@code shapeGraph} inside this opaque body. The SHACL validation
   * playground ({@code /shapes/validate?templateAppId=<...>}) reads
   * that field via {@code frontend/utils/shaclTemplateBody.ts} and
   * pre-fills the shape-graph textarea when present. The Java IO does
   * not parse the body -- the convention is forward-compat and
   * frontend-extracted -- but the convention is recorded here so
   * template authors can discover it.
   */
  @Schema(required = true, description = "Recipe body. JSON DSL per aidocs/54 §7 (opaque to the entity; validation is service-layer). Optional field `shapeGraph` (Turtle string) is read by /shapes/validate per SHAPES-V-PREFILL-3-EXTRACT-SHACL.")
  private String body;

  @Schema(required = false, nullable = true, description = "Markdown-flavoured human description.")
  private String description;

  @Schema(required = false, nullable = true, description = "Author-supplied tags for picker filtering.")
  private List<String> tags;

  @Schema(required = false, nullable = true, description = "Display name of the user who minted (or copy-on-write-edited) the row (UUID-shaped Keycloak subjects are redacted).")
  private String createdBy;

  @Schema(required = false, nullable = true, description = "Millis since epoch when the row was created.")
  private Long createdAt;

  @Schema(required = false, nullable = true, description = "Millis since epoch when the row was last touched.")
  private Long updatedAt;

  @Schema(required = true, description = "true when retired and filtered from picker listings (still kept on disk).")
  private boolean retired;

  @Schema(required = false, nullable = true, description = "MDI icon name, e.g. mdi-layers. Null means use the per-kind default.")
  private String iconKey;

  public static ShepardTemplateIO from(ShepardTemplate t) {
    return new ShepardTemplateIO(
      t.getAppId(),
      t.getName(),
      t.getTemplateKind(),
      t.getVersion(),
      t.getBody(),
      t.getDescription(),
      t.getTags(),
      DisplayNameResolver.redactUsername(t.getCreatedBy()),
      t.getCreatedAt(),
      t.getUpdatedAt(),
      t.isRetired(),
      t.getIconKey()
    );
  }
}
