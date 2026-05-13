package de.dlr.shepard.plugins.minter.datacite.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * KIP1d — CLI-side DTO mirroring the server's
 * {@code DataciteMinterConfigIO} for human-readable + JSON output.
 *
 * <p>Lives in the CLI sub-package so the CLI module doesn't import
 * the backend-side IO record (which carries Quarkus-only types in
 * other plugins). Jackson unmarshals the same JSON shape into this
 * POJO at the wire boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class DataciteConfig {

  private boolean enabled;
  private String apiBaseUrl;
  private String handlePrefix;
  private String repositoryId;
  private boolean passwordSet;
  private String passwordFingerprint;
  private String publisher;
  private String landingPageBase;
  private String defaultState;
  private Date updatedAt;
  private String updatedBy;
}
