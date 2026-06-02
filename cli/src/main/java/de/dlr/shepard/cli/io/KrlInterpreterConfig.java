package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KRL-CONFIG-1 — CLI-side mirror of {@code KrlInterpreterConfigIO} from
 * {@code GET/PATCH /v2/admin/krl/config}.
 *
 * <p>{@code enabled} is the master switch for the KRL interpreter sidecar.
 * {@code sidecarUrl} is the base URL the sidecar is reachable at (may be
 * null when the deploy-time default is used). {@code timeoutSeconds} and
 * {@code maxBodySizeMb} reflect the effective values (runtime if set, else
 * deploy-time defaults).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class KrlInterpreterConfig {

  private final boolean enabled;
  private final String sidecarUrl;
  private final int timeoutSeconds;
  private final int maxBodySizeMb;

  public KrlInterpreterConfig(
      @JsonProperty("enabled") boolean enabled,
      @JsonProperty("sidecarUrl") String sidecarUrl,
      @JsonProperty("timeoutSeconds") int timeoutSeconds,
      @JsonProperty("maxBodySizeMb") int maxBodySizeMb) {
    this.enabled = enabled;
    this.sidecarUrl = sidecarUrl;
    this.timeoutSeconds = timeoutSeconds;
    this.maxBodySizeMb = maxBodySizeMb;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getSidecarUrl() {
    return sidecarUrl;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public int getMaxBodySizeMb() {
    return maxBodySizeMb;
  }
}
