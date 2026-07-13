package de.dlr.shepard.v2.provenance.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for APISIMP-PROVENANCE-STATS-EPOCH-MS-TO-ISO: ProvenanceStatsIO.toIso(). */
class ProvenanceStatsIOTest {

  @Test
  void toIso_knownTimestamp_isUtc() {
    // 2026-07-13T10:00:00Z = 1783936800000 ms
    String iso = ProvenanceStatsIO.toIso(1783936800000L);
    assertEquals("2026-07-13T10:00:00Z", iso);
  }

  @Test
  void toIso_epochZero_isUnixEpoch() {
    assertEquals("1970-01-01T00:00:00Z", ProvenanceStatsIO.toIso(0L));
  }
}
