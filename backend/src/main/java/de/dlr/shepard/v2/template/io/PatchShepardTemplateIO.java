package de.dlr.shepard.v2.template.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PATCH body for {@code PATCH /v2/templates/{appId}}.
 *
 * <p>Every field is nullable — only supplied fields apply. Editing
 * triggers a copy-on-write per {@code aidocs/54 §7}: a new
 * {@code :ShepardTemplate} node is minted with {@code version + 1};
 * the prior row is marked {@code retired = true}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PatchShepardTemplate")
public class PatchShepardTemplateIO {

  @Schema(required = false, nullable = true, description = "If set, replaces the human-readable name.")
  private String name;

  @Schema(required = false, nullable = true, description = "If set, replaces the JSON DSL body.")
  private String body;

  @Schema(required = false, nullable = true, description = "If set, replaces the description.")
  private String description;

  @Schema(required = false, nullable = true, description = "If set, replaces the tag list (full replace, not merge).")
  private List<String> tags;
}
