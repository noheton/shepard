package de.dlr.shepard.plugins.minter.epic.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** KIP1c — CLI-side DTO for the credential-set response. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class EpicCredentialSet {

  private boolean credentialSet;
  private String fingerprint;
}
