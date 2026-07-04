package de.dlr.shepard.plugins.hdf5.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FTOGGLE-CLI-PARITY-1 — CLI-side mirror of {@code HdfConfigIO} from
 * {@code GET/PATCH /v2/admin/config/hdf}.
 *
 * <p>{@code enabled} may be {@code null} when the server has not yet
 * resolved it against the deploy-time default; the CLI renders this as
 * {@code "(default)"}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HdfCliConfig {

  private Boolean enabled;

  public HdfCliConfig(@JsonProperty("enabled") Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getEnabled() {
    return enabled;
  }
}
