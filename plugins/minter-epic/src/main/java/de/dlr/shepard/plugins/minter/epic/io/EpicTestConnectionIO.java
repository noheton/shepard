package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1c — response body for
 * {@code POST /v2/admin/minters/epic/test-connection}.
 *
 * <p>Reports whether the configured ePIC API is reachable, with
 * the HTTP status code observed (or 0 on network failure) and the
 * round-trip latency as an ISO 8601 duration string. Useful for
 * operators to verify their config before enabling minting.
 */
@Schema(name = "EpicTestConnectionIO", description = "Response body for POST /v2/admin/minters/epic/test-connection — ePIC API reachability result.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EpicTestConnectionIO(
  boolean reachable,
  int statusCode,
  @Schema(description = "ISO 8601 duration of the round-trip probe, e.g. PT0.123S.", example = "PT0.123S")
  String latency,
  String apiBaseUrl,
  String detail
) {}
