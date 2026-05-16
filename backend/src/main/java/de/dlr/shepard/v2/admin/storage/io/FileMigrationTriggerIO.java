package de.dlr.shepard.v2.admin.storage.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** FS1e1 — request body for {@code POST /v2/admin/files/migrate}. */
@Data
@NoArgsConstructor
public class FileMigrationTriggerIO {

  @Schema(
    description = "Id of the storage adapter to migrate files away from.",
    example = "gridfs",
    required = true
  )
  private String sourceProviderId;

  @Schema(
    description = "Id of the storage adapter to migrate files to.",
    example = "s3",
    required = true
  )
  private String targetProviderId;
}
