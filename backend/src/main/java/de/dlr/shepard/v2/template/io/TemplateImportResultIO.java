package de.dlr.shepard.v2.template.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Typed response envelope for {@code POST /v2/templates/import} (T1f,
 * APISIMP-TEMPLATE-IMPORT-BARE-LIST).
 *
 * <p>Carries the full list of created/updated templates plus per-operation
 * counts so callers do not have to infer them by counting list elements.
 */
@Schema(name = "TemplateImportResult")
public record TemplateImportResultIO(
  @Schema(description = "All templates written during this import (created + updated).")
  List<ShepardTemplateIO> items,

  @Schema(description = "Number of templates minted as brand-new entries (no prior version with same name + kind).")
  int created,

  @Schema(description = "Number of templates produced via copy-on-write (an existing live version was retired and a new version minted).")
  int updated,

  @Schema(description = "Number of templates skipped (reserved for future validation gates; always 0 in the current implementation).")
  int skipped
) {}
