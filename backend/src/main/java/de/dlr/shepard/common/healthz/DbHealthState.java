package de.dlr.shepard.common.healthz;

import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Tracks the most recent ping outcome for one database so that startup,
 * readiness and liveness checks can share a single result.
 */
public final class DbHealthState {

  private volatile boolean everUp = false;
  private volatile long lastSuccessfulPingMs = 0L;
  private volatile long lastLatencyMs = -1L;
  private volatile String lastErrorClass = null;
  private volatile String lastErrorMessage = null;

  public synchronized void recordSuccess(long latencyMs) {
    this.everUp = true;
    this.lastSuccessfulPingMs = System.currentTimeMillis();
    this.lastLatencyMs = latencyMs;
    this.lastErrorClass = null;
    this.lastErrorMessage = null;
  }

  public synchronized void recordFailure(long latencyMs, Throwable t) {
    this.lastLatencyMs = latencyMs;
    this.lastErrorClass = t == null ? "Unknown" : t.getClass().getName();
    this.lastErrorMessage = t == null ? "unknown error" : String.valueOf(t.getMessage());
  }

  public boolean hasEverBeenUp() {
    return everUp;
  }

  public long getLastSuccessfulPingMs() {
    return lastSuccessfulPingMs;
  }

  public long ageMs() {
    if (lastSuccessfulPingMs == 0L) return Long.MAX_VALUE;
    return System.currentTimeMillis() - lastSuccessfulPingMs;
  }

  public boolean isFreshWithin(long maxStalenessMs) {
    return everUp && ageMs() <= maxStalenessMs;
  }

  public HealthCheckResponseBuilder writeData(HealthCheckResponseBuilder builder) {
    builder.withData("lastSuccessfulPingMs", lastSuccessfulPingMs);
    builder.withData("latencyMs", lastLatencyMs);
    if (lastErrorClass != null) {
      builder.withData("errorClass", lastErrorClass);
      builder.withData("errorMessage", lastErrorMessage == null ? "" : lastErrorMessage);
    }
    return builder;
  }
}
