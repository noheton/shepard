package de.dlr.shepard.v2.admin.storage.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Result of rolling back a single file's storage-backend migration. */
@Schema(description = "Result of rolling back a single file's storage-backend migration.")
public record FileMigrationRollbackResultIO(String appId, String status) {}
