package de.dlr.shepard.v2.transform.urscript.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * URSCRIPT-TRAJECTORY-1 — deploy-time configuration for the URScript interpreter
 * sidecar client.
 *
 * <h2>Tier-1 (this row): deploy-time only</h2>
 * <p>Operators set these in {@code application.properties} or via
 * environment variables. Runtime-mutable {@code :UrScriptInterpreterConfig}
 * (admin REST + CLI parity, per the "Always: surface operator knobs in
 * the admin config" rule) is deferred to a follow-up.
 *
 * <p>URSCRIPT-TRAJECTORY-1: URScript interpret is a MAPPING_RECIPE transform —
 * the sidecar client is reused by {@code UrScriptTrajectoryTransformExecutor}.
 * Materialize stays callable while the sidecar is down: the executor surfaces a
 * recoverable 4xx transform error rather than failing the whole instance.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code shepard.urscript.interpreter.url} — sidecar base URL.
 *       Default: {@code http://urscript-interpreter:8080}.</li>
 *   <li>{@code shepard.urscript.interpreter.timeout-seconds} — per-call request
 *       timeout. Default: {@code 120}.</li>
 *   <li>{@code shepard.urscript.interpreter.max-body-size-mb} — guard against
 *       runaway payloads. Default: {@code 16}.</li>
 * </ul>
 */
@ApplicationScoped
public class UrScriptInterpreterConfig {

  @ConfigProperty(
    name = "shepard.urscript.interpreter.url",
    defaultValue = "http://urscript-interpreter:8080"
  )
  String sidecarUrl;

  @ConfigProperty(name = "shepard.urscript.interpreter.timeout-seconds", defaultValue = "120")
  int timeoutSeconds;

  @ConfigProperty(name = "shepard.urscript.interpreter.max-body-size-mb", defaultValue = "16")
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
