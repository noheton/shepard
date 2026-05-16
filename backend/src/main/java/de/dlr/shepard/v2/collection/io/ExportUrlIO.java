package de.dlr.shepard.v2.collection.io;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FS1g — response body for {@code POST /v2/collections/{appId}/export-url}.
 * Contains the presigned GET URL to download the RO-Crate ZIP directly
 * from S3, along with the suggested filename and expiry time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportUrlIO {

  @Schema(
    description = "Presigned GET URL. Issue an HTTP GET to download the RO-Crate ZIP directly " +
    "from storage. No authentication headers are required. Expires after 30 minutes.",
    example = "https://storage.example.com/shepard/exports/uuid.zip?X-Amz-Signature=..."
  )
  private String downloadUrl;

  @Schema(
    description = "Suggested filename for the downloaded ZIP.",
    example = "my-collection-export.zip"
  )
  private String fileName;

  @Schema(description = "When the presigned URL expires.", example = "2024-08-15T11:53:44Z")
  private Instant expiresAt;
}
