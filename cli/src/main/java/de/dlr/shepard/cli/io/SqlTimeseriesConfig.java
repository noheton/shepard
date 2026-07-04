package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FTOGGLE-CLI-PARITY-1 — CLI-side mirror of the JSON shape returned by
 * {@code GET/PATCH /v2/admin/sql-timeseries/config}.
 *
 * <p>{@code maxRows} and {@code maxDuration} are resolved (non-null) by the
 * server; {@code enabled} is populated once FTOGGLE-SQL-ENABLE-1 ships.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SqlTimeseriesConfig {

  private Boolean enabled;
  private long maxRows;
  private String maxDuration;

  public SqlTimeseriesConfig(
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("maxRows") long maxRows,
    @JsonProperty("maxDuration") String maxDuration
  ) {
    this.enabled = enabled;
    this.maxRows = maxRows;
    this.maxDuration = maxDuration;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public long getMaxRows() {
    return maxRows;
  }

  public String getMaxDuration() {
    return maxDuration;
  }
}
