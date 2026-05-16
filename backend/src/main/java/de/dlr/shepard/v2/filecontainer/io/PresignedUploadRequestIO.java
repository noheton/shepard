package de.dlr.shepard.v2.filecontainer.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
public class PresignedUploadRequestIO {

  @Schema(description = "Original file name (used for Content-Disposition).", example = "sensor-data.csv")
  private String fileName;
}
