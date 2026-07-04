package de.dlr.shepard.v2.transform.krl.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KRL-INTERPRETER-05 — deploy-time configuration for the sidecar client.
 *
 * <h2>Tier-1 (this row): deploy-time only</h2>
 * <p>Operators set these in {@code application.properties} or via
 * environment variables. Runtime-mutable {@code :KrlInterpreterConfig}
 * (admin REST + CLI parity, per the "Always: surface operator knobs in
 * the admin config" rule) is deferred to {@code KRL-CONFIG-1}.
 *
 * <p>V2CONV-B5: KRL interpret is now a MAPPING_RECIPE transform — the sidecar
 * client is reused by {@code KrlTrajectoryTransformExecutor}. Materialize stays
 * callable while the sidecar is down: the executor surfaces a recoverable 4xx
 * transform error rather than failing the whole instance.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code shepard.krl.sidecar.url} — sidecar base URL.
 *       Default: {@code http://krl-interpreter-sidecar:8000}.</li>
 *   <li>{@code shepard.krl.sidecar.timeout-seconds} — per-call request
 *       timeout. Default: {@code 120}.</li>
 *   <li>{@code shepard.krl.sidecar.max-body-size-mb} — guard against
 *       runaway payloads (the backend rejects requests whose summed
 *       file payloads exceed this). Default: {@code 16}.</li>
 * </ul>
 */
@ApplicationScoped
public class KrlInterpreterConfig {

  @ConfigProperty(
    name = "shepard.krl.sidecar.url",
    defaultValue = "http://krl-interpreter-sidecar:8000"
  )
  String sidecarUrl;

  @ConfigProperty(name = "shepard.krl.sidecar.timeout-seconds", defaultValue = "120")
  int timeoutSeconds;

  @ConfigProperty(name = "shepard.krl.sidecar.max-body-size-mb", defaultValue = "16")
  int maxBodySizeMb;

  public String getSidecarUrl() {
    return sidecarUrl;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public int getMaxBodySizeMb() {
    return maxBodySizeMb;
  }

  /** For tests. */
  public void setSidecarUrl(String sidecarUrl) {
    this.sidecarUrl = sidecarUrl;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public void setMaxBodySizeMb(int maxBodySizeMb) {
    this.maxBodySizeMb = maxBodySizeMb;
  }
}
