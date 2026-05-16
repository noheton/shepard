package de.dlr.shepard.v2.admin.storage.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** FS1e1 — response shape for {@code GET /v2/admin/storage}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatusIO {

  @Schema(
    description = "Id of the currently active storage adapter, or 'none' when no adapter is configured.",
    example = "gridfs",
    nullable = true
  )
  private String activeProviderId;

  @Schema(description = "All discovered storage adapters with their id, enabled state, and active flag.")
  private List<StorageAdapterIO> adapters;
}
