package de.dlr.shepard.v2.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * APISIMP-COLLECTION-EVENT-EPOCH-MS-TO-ISO — verifies that
 * {@link CollectionEventIO#toIso} produces ISO 8601 UTC strings.
 */
class CollectionEventIOTest {

  @Test
  void toIso_epoch0_yields1970() {
    assertEquals("1970-01-01T00:00:00Z", CollectionEventIO.toIso(0L));
  }

  @Test
  void toIso_roundMillis_includesSubSecond() {
    // 1000ms = 1s exactly
    assertEquals("1970-01-01T00:00:01Z", CollectionEventIO.toIso(1000L));
  }

  @Test
  void toIso_subSecond_includesMillis() {
    // 999ms → ".999Z" sub-second fraction
    assertEquals("1970-01-01T00:00:00.999Z", CollectionEventIO.toIso(999L));
  }

  @Test
  void toIso_knownTimestamp_isUtc() {
    // 2026-07-13T10:00:00Z = 1783936800000ms
    String iso = CollectionEventIO.toIso(1783936800000L);
    assertEquals("2026-07-13T10:00:00Z", iso);
  }

  @Test
  void toIso_result_endsWithZ() {
    assertTrue(CollectionEventIO.toIso(System.currentTimeMillis()).endsWith("Z"),
      "ISO timestamp must be UTC (ends with Z)");
  }

  @Test
  void record_timestamp_field_isString() throws NoSuchMethodException {
    // Verify the wire field type changed from long to String.
    var method = CollectionEventIO.class.getMethod("timestamp");
    assertEquals(String.class, method.getReturnType(),
      "CollectionEventIO.timestamp() must return String (not long)");
  }

  @Test
  void heartbeatEvent_timestampIsIsoString() {
    String ts = CollectionEventIO.toIso(System.currentTimeMillis());
    CollectionEventIO hb = new CollectionEventIO("HEARTBEAT", null, null, null, null, ts);
    assertNull(hb.entityAppId());
    assertTrue(hb.timestamp().endsWith("Z"), "heartbeat timestamp must be ISO UTC string");
  }
}
