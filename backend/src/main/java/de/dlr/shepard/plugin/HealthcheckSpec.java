package de.dlr.shepard.plugin;

import java.time.Duration;
import java.util.Objects;

/**
 * PM1f — readiness probe for a {@link SidecarSpec}.
 *
 * <p>The operator-side bootstrap waits on the healthcheck before
 * running {@link SidecarSpec#postInit()} — it would be unsafe to,
 * say, run {@code /garage bucket create} against a Garage sidecar
 * that hasn't finished booting.
 *
 * <p>The shape mirrors docker-compose's {@code healthcheck:}
 * block (cmd / interval / timeout / retries) because that's the
 * substrate every operator's tooling knows. Kubernetes / nomad
 * adapters translate at render time.
 *
 * @param cmd shell command to run inside the sidecar container;
 *   exit 0 = healthy, non-zero = unhealthy. Should be POSIX-shell
 *   compatible. Example: {@code "curl -fsS http://localhost:3900/health"}.
 * @param interval how often to retry the probe (between successful
 *   runs). Translates to {@code interval:} in docker-compose.
 * @param timeout maximum time one probe may take before being
 *   considered failed.
 * @param retries number of consecutive failed probes before the
 *   sidecar is marked unhealthy. Minimum 1.
 */
public record HealthcheckSpec(
  String cmd,
  Duration interval,
  Duration timeout,
  int retries
) {
  public HealthcheckSpec {
    Objects.requireNonNull(cmd, "cmd");
    Objects.requireNonNull(interval, "interval");
    Objects.requireNonNull(timeout, "timeout");
    if (cmd.isBlank()) {
      throw new IllegalArgumentException("healthcheck cmd must be non-blank");
    }
    if (interval.isZero() || interval.isNegative()) {
      throw new IllegalArgumentException(
        "healthcheck interval must be positive — was " + interval
      );
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException(
        "healthcheck timeout must be positive — was " + timeout
      );
    }
    if (retries < 1) {
      throw new IllegalArgumentException(
        "healthcheck retries must be >= 1 — was " + retries
      );
    }
  }
}
