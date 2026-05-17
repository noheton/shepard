package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * KIP1c — response body for
 * {@code POST /v2/admin/minters/epic/test-connection}.
 *
 * <p>Reports whether the configured ePIC API is reachable, with
 * the HTTP status code observed (or 0 on network failure) and the
 * round-trip latency in millis. Useful for operators to verify
 * their config before enabling minting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicTestConnectionIO(
  boolean reachable,
  int statusCode,
  long latencyMs,
  String apiBaseUrl,
  String detail
) {}
