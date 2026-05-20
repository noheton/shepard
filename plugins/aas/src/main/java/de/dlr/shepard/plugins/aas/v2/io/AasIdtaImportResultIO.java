package de.dlr.shepard.plugins.aas.v2.io;

import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Result shape for {@code POST /v2/admin/aas/import-idta-templates} (AAS1d).
 *
 * <p>{@code created} lists templates that were newly minted or copy-on-write
 * updated during this invocation. {@code skipped} counts templates whose
 * body, description, and tags were already identical to the live record —
 * no write performed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AasIdtaImportResult")
public class AasIdtaImportResultIO {

  @Schema(required = true, description = "Templates created or updated during this import run.")
  private List<ShepardTemplateIO> created;

  @Schema(required = true, description = "Count of templates skipped because their content was already current.")
  private int skipped;
}
