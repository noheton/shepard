package de.dlr.shepard.v2.admin.semantic.io;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-003 — RFC 7396 merge-patch body for
 * {@code PATCH /v2/admin/semantic/config}.
 *
 * <p>All fields are nullable; a {@code null} value in the JSON body means
 * "leave this field unchanged" (standard merge-patch semantics, consistent
 * with other PATCH endpoints in this fork).
 */
@Data
@NoArgsConstructor
@Schema(name = "PatchSemanticConfig")
public class PatchSemanticConfigIO {

  @Schema(description = "Master toggle for ontology pre-seed. Null = leave unchanged.", nullable = true)
  private Boolean preseedEnabled;

  @Schema(description = "Replace the full disabled-bundles list. Null = leave unchanged.", nullable = true)
  private List<String> disabledBundles;

  @Schema(description = "appId of the default Vocabulary. Null = leave unchanged; empty string = clear.", nullable = true)
  private String defaultVocabularyAppId;

  @Schema(description = "Annotation mode: STRICT or PERMISSIVE. Null = leave unchanged.", nullable = true)
  private String annotationMode;

  @Schema(description = "Enable/disable AI suggestion flow. Null = leave unchanged.", nullable = true)
  private Boolean suggestionEnabled;

  @Schema(description = "AI model identifier. Null = leave unchanged; empty string = clear.", nullable = true)
  private String suggestionModelId;

  // ─── SEMA-V6-013 fields ──────────────────────────────────────────────────

  @Schema(
    description = "SEMA-V6-013 — annotation delete policy: 'author-or-manager', 'author-only', " +
      "or 'manager-only'. Null = leave unchanged; empty string = clear (revert to default).",
    nullable = true
  )
  private String annotationDeletePolicy;
}
