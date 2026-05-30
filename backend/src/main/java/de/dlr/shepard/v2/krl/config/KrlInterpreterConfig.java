package de.dlr.shepard.v2.krl.config;

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
 * <p>The endpoint stays callable while the sidecar is down: requests
 * return {@code 502 Bad Gateway}; that's the documented expected
 * behaviour and is verified by {@link
 * de.dlr.shepard.v2.krl.services.KrlInterpretServiceTest}.
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
