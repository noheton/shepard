package de.dlr.shepard.v2.admin.storage.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** FS1e1 — per-adapter entry in the {@code GET /v2/admin/storage} response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageAdapterIO {

  @Schema(description = "Stable adapter id (e.g. 'gridfs', 's3').", example = "s3")
  private String id;

  @Schema(description = "Whether this adapter is currently usable (credentials present, upstream reachable).")
  private boolean enabled;

  @Schema(description = "Whether this adapter is the currently active provider for new uploads.")
  private boolean active;
}
