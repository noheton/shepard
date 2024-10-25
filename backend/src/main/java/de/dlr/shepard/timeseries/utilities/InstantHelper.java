package de.dlr.shepard.timeseries.utilities;

import java.time.Instant;

public class InstantHelper {

  private Instant instant = Instant.now();

  public InstantHelper now() {
    this.instant = Instant.now();
    return this;
  }

  public InstantHelper addSeconds(int seconds) {
    this.instant.plusSeconds(seconds);
    return this;
  }

  public long toNanoseconds() {
    return this.instant.toEpochMilli() * 1_000_000;
  }
}
