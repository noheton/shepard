package de.dlr.shepard.v2.admin.semantic.io;

import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-003 — read projection for {@link SemanticConfig}.
 *
 * <p>Returned by {@code GET /v2/admin/semantic/config}.
 * Contains all operator-visible fields including the four new v6 fields.
 */
@Data
@NoArgsConstructor
@Schema(name = "SemanticConfig")
public class SemanticConfigIO {

  @Schema(readOnly = true)
  private String appId;

  @Schema(description = "Master toggle for ontology pre-seed on startup. Default: true.")
  private boolean preseedEnabled;

  @Schema(description = "Bundle ids that are runtime-disabled. An empty list means all bundles are enabled.")
  private List<String> disabledBundles;

  // ─── SEMA-V6-003 fields ──────────────────────────────────────────────────

  @Schema(description = "appId of the Vocabulary node pre-selected in the annotation dialog. Null = no default.", nullable = true)
  private String defaultVocabularyAppId;

  @Schema(description = "Annotation validation mode: STRICT (only registered predicates) or PERMISSIVE (free-form allowed). Default: PERMISSIVE.")
  private String annotationMode;

  @Schema(description = "When true, AI annotation suggestions are enabled. Default: false.")
  private boolean suggestionEnabled;

  @Schema(description = "Identifier of the AI model used for annotation suggestions. Null = server default.", nullable = true)
  private String suggestionModelId;

  // ─── SEMA-V6-013 fields ──────────────────────────────────────────────────

  @Schema(
    description = "SEMA-V6-013 — who may delete annotations: 'author-or-manager' (default), " +
      "'author-only', or 'manager-only'. Null = default (author-or-manager).",
    nullable = true
  )
  private String annotationDeletePolicy;

  /** Map from entity. */
  public static SemanticConfigIO from(SemanticConfig c) {
    SemanticConfigIO io = new SemanticConfigIO();
    io.appId                  = c.getAppId();
    io.preseedEnabled         = c.isPreseedEnabled();
    io.disabledBundles        = c.getDisabledBundles() == null ? List.of() : c.getDisabledBundles();
    io.defaultVocabularyAppId = c.getDefaultVocabularyAppId();
    io.annotationMode         = c.getAnnotationMode();
    io.suggestionEnabled      = c.isSuggestionEnabled();
    io.suggestionModelId      = c.getSuggestionModelId();
    io.annotationDeletePolicy = c.getAnnotationDeletePolicy();
    return io;
  }
}
