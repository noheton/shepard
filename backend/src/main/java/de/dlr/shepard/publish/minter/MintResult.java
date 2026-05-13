package de.dlr.shepard.publish.minter;

import java.time.Instant;

/**
 * Output of a successful {@link Minter#mint(MintRequest)} call.
 *
 * <p>KIP1a baseline shape per {@code aidocs/66 §3-§5}. The {@link #pid}
 * is the freshly-minted persistent identifier (a Handle / DOI / mock
 * string depending on the active minter); {@link #mintedAt} is the
 * server-side wall-clock at the moment the minter returned. The
 * {@link #minterId} carries the {@link Minter#id()} of the adapter
 * that produced the row — stamped onto the {@code :Publication} node
 * so an operator can trace "which minter minted this PID" later,
 * which matters when KIP1c (ePIC) and KIP1d (DataCite) plugins ship.
 *
 * <p>A failed mint is signalled by the minter throwing — never by
 * returning a partially-populated result.
 */
public record MintResult(String pid, Instant mintedAt, String minterId) {
  public MintResult {
    if (pid == null || pid.isBlank()) {
      throw new IllegalArgumentException("pid must not be null/blank");
    }
    if (mintedAt == null) {
      throw new IllegalArgumentException("mintedAt must not be null");
    }
    if (minterId == null || minterId.isBlank()) {
      throw new IllegalArgumentException("minterId must not be null/blank");
    }
  }
}
