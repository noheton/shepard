package de.dlr.shepard.v2.filecontainer.io;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrlIO {

  @Schema(
    description = "Presigned PUT URL. Send a single HTTP PUT request to this URL with the file bytes as the body. " +
    "No authentication headers are required; the signature is embedded in the URL.",
    example = "https://storage.example.com/shepard/container123/uuid?X-Amz-Signature=..."
  )
  private String uploadUrl;

  @Schema(
    description = "Object identifier (UUID) assigned to this file. " +
    "Pass this to the commit endpoint after the upload completes.",
    example = "550e8400-e29b-41d4-a716-446655440000"
  )
  private String oid;

  @Schema(description = "When the presigned URL expires.", example = "2024-08-15T11:33:44Z")
  private Instant expiresAt;
}
