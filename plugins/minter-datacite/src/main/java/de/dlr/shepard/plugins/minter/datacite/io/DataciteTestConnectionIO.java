package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * KIP1d — response body for
 * {@code POST /v2/admin/minters/datacite/test-connection}.
 *
 * <p>Reports whether the configured DataCite API is reachable, with
 * the HTTP status code observed (or 0 on network failure) and the
 * round-trip latency in millis. Useful for operators to verify
 * their config before enabling the minter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataciteTestConnectionIO(
  boolean reachable,
  int statusCode,
  long latencyMs,
  String apiBaseUrl,
  String detail
) {}
