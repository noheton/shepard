package de.dlr.shepard.v2.template.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Body for {@code PUT /v2/collections/{appId}/templates/allowed}.
 * Replaces the {@code :ALLOWS_TEMPLATE} edge set for the Collection
 * with the provided list — full replace, not merge.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AllowedTemplates")
public class AllowedTemplatesIO {

  @Schema(
    required = true,
    description = "Template appIds the Collection owner permits inside this Collection. Empty list = no curation (picker falls back to all live templates)."
  )
  private List<String> templateAppIds;
}
