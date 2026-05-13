package de.dlr.shepard.plugins.minter.datacite.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** KIP1d — CLI-side DTO for the test-connection diagnostic response. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class DataciteTestConnection {

  private boolean reachable;
  private int statusCode;
  private long latencyMs;
  private String apiBaseUrl;
  private String detail;
}
