package de.dlr.shepard.v2.filecontainer.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
public class UploadCommitIO {

  @Schema(
    required = true,
    description = "The oid returned by the upload-url endpoint.",
    example = "550e8400-e29b-41d4-a716-446655440000"
  )
  private String oid;

  @Schema(
    required = true,
    description = "File name to register for this file.",
    example = "sensor-data.csv"
  )
  private String fileName;

  @Schema(description = "MIME type of the uploaded file.", example = "text/csv", nullable = true)
  private String contentType;

  @Schema(description = "File size in bytes. Optional but recommended.", nullable = true, example = "204800")
  private Long fileSize;
}
