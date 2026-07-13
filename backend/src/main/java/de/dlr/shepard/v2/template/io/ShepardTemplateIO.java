package de.dlr.shepard.v2.template.io;

import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.template.entities.ShepardTemplate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
   * SHAPES-V-PREFILL-3-EXTRACT-SHACL — Template authors may embed a
   * SHACL shape graph (Turtle) under the optional JSON field
   * {@code shapeGraph} inside this opaque body. The SHACL validation
   * playground ({@code /shapes/validate?templateAppId=<...>}) reads
   * that field via {@code frontend/utils/shaclTemplateBody.ts} and
   * pre-fills the shape-graph textarea when present. The Java IO does
   * not parse the body — the convention is forward-compat and
   * frontend-extracted — but the convention is recorded here so
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

  @Schema(required = false, nullable = true, description = "ISO 8601 UTC timestamp when the row was created.")
  private String createdAt;

  @Schema(required = false, nullable = true, description = "ISO 8601 UTC timestamp when the row was last touched.")
  private String updatedAt;

  @Schema(required = true, description = "true when retired and filtered from picker listings (still kept on disk).")
  private boolean retired;

  @Schema(
    required = false,
    nullable = true,
    description = "MDI (Material Design Icons) name with the 'mdi-' prefix, e.g. 'mdi-layers'. " +
    "Null means the UI uses the per-kind default. Design: aidocs/integrations/122."
  )
  private String iconKey;

  @Schema(
    required = false,
    nullable = true,
    description = "appId of the parent template this template extends (single-parent inheritance). " +
    "Null means a root template. Child fields override parent on collision. Design: aidocs/integrations/123."
  )
  private String parentTemplateAppId;

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
      toIso(t.getCreatedAt()),
      toIso(t.getUpdatedAt()),
      t.isRetired(),
      t.getIconKey(),
      t.getParentTemplateAppId()
    );
  }

  private static String toIso(Long epochMs) {
    if (epochMs == null) return null;
    return DateTimeFormatter.ISO_INSTANT.format(
      Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC)
    );
  }
}
