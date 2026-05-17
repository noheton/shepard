package de.dlr.shepard.plugins.minter.epic.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** KIP1c — CLI-side DTO for the test-connection diagnostic response. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class EpicTestConnection {

  private boolean reachable;
  private int statusCode;
  private long latencyMs;
  private String apiBaseUrl;
  private String detail;
}
