package de.dlr.shepard.v2.filecontainer.io;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedDownloadUrlIO {

  @Schema(
    description = "Presigned GET URL. Issue an HTTP GET to this URL to download the file bytes directly " +
    "from storage. No authentication headers are required.",
    example = "https://storage.example.com/shepard/container123/uuid?X-Amz-Signature=..."
  )
  private String downloadUrl;

  @Schema(description = "When the presigned URL expires.", example = "2024-08-15T11:23:44Z")
  private Instant expiresAt;
}
