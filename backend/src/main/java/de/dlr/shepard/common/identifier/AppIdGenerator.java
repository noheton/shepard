package de.dlr.shepard.common.identifier;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Generates application-level identifiers as UUID v7.
 *
 * <p>UUID v7 (RFC 9562) embeds a millisecond Unix timestamp in the high 48 bits
 * followed by random bits, so identifiers minted on the same node are
 * monotonic-per-millisecond and globally unique without coordination. That
 * shape is what L2 of the Neo4j-ID migration (see
 * {@code aidocs/25-neo4j-id-migration-design.md} §2) needs: a stable, sortable,
 * collision-free string that composes with cursor pagination per
 * {@code aidocs/12 §11.A.2} and {@code aidocs/13 §2.6} without requiring a
 * round-trip to the database to allocate it.
 *
 * <p>Backed by {@code com.github.f4b6a3:uuid-creator}'s
 * {@link UuidCreator#getTimeOrderedEpoch()} — the canonical Java UUID v7
 * generator. Output is the canonical 36-character lowercase hyphenated form
 * (e.g. {@code 0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506}).
 */
public final class AppIdGenerator {

  private AppIdGenerator() {
    // utility class
  }

  /**
   * @return a fresh UUID v7 in canonical 36-character string form.
   */
  public static String next() {
    return UuidCreator.getTimeOrderedEpoch().toString();
  }
}
