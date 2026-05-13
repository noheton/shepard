package de.dlr.shepard.plugins.minter.datacite.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** KIP1d — CLI-side DTO for the credential-set response. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class DataciteCredentialSet {

  private boolean passwordSet;
  private String fingerprint;
}
