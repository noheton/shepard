package de.dlr.shepard.plugins.storage.s3.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FS1b — CLI-side DTO for the credential-set response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class S3CredentialSet {

  private boolean secretKeySet;
  private String secretKeyFingerprint;
}
