package de.dlr.shepard.plugins.storage.s3.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FS1b — CLI-side DTO for the test-connection response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class S3TestConnection {

  private boolean reachable;
  private int statusCode;
  private long latencyMs;
  private String endpoint;
  private String bucket;
  private String detail;
}
