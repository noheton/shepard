package de.dlr.shepard.v2.timeseriescontainer.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-TIMESERIES-DATAPOINT-TS — unit coverage for the v2 data-point IO conversions.
 *
 * <p>Covers: ns → ISO 8601 string, ISO 8601 string → ns, round-trip, sub-second precision,
 * bad-timestamp rejection on the ingest side.
 */
public class TimeseriesDataPointV2IOTest {

  // ── nsToIso / from() ────────────────────────────────────────────────────────

  @Test
  void nsToIso_wholeSecond() {
    // 1_000_000_000 ns = 1970-01-01T00:00:01Z
    TimeseriesDataPointV2IO io =
        TimeseriesDataPointV2IO.from(new TimeseriesDataPoint(1_000_000_000L, 42.0));
    assertEquals("1970-01-01T00:00:01Z", io.timestamp());
    assertEquals(42.0, io.value());
  }

  @Test
  void nsToIso_nanosecondPrecision() {
    // 1_000_000_123 ns = 1970-01-01T00:00:01.000000123Z
    TimeseriesDataPointV2IO io =
        TimeseriesDataPointV2IO.from(new TimeseriesDataPoint(1_000_000_123L, "hello"));
    assertEquals("1970-01-01T00:00:01.000000123Z", io.timestamp());
  }

  @Test
  void nsToIso_millisecondPrecision() {
    // 1_000_500_000_000 ns = 1970-01-01T00:16:40.5Z
    TimeseriesDataPointV2IO io =
        TimeseriesDataPointV2IO.from(new TimeseriesDataPoint(1_000_500_000_000L, true));
    assertTrue(io.timestamp().startsWith("1970-01-01T00:16:40"));
  }

  // ── isoToNs (used by ingest path) ───────────────────────────────────────────

  @Test
  void isoToNs_wholeSecond() {
    long ns = TimeseriesDataPointV2IO.isoToNs("1970-01-01T00:00:01Z");
    assertEquals(1_000_000_000L, ns);
  }

  @Test
  void isoToNs_nanosecondPrecision() {
    long ns = TimeseriesDataPointV2IO.isoToNs("1970-01-01T00:00:01.000000123Z");
    assertEquals(1_000_000_123L, ns);
  }

  @Test
  void isoToNs_millisecondPrecision() {
    long ns = TimeseriesDataPointV2IO.isoToNs("2024-06-01T08:00:00.500Z");
    // 0.5 s = 500_000_000 ns
    assertEquals(500_000_000L, ns % 1_000_000_000L);
  }

  @Test
  void isoToNs_stripsLeadingTrailingWhitespace() {
    long ns = TimeseriesDataPointV2IO.isoToNs("  1970-01-01T00:00:01Z  ");
    assertEquals(1_000_000_000L, ns);
  }

  // ── round-trip: ns → ISO → ns ───────────────────────────────────────────────

  @Test
  void roundTrip_preservesNanosecondTimestamp() {
    long original = 1_717_228_800_123_456_789L; // a realistic sensor timestamp
    String iso = TimeseriesDataPointV2IO.from(new TimeseriesDataPoint(original, 0)).timestamp();
    long recovered = TimeseriesDataPointV2IO.isoToNs(iso);
    assertEquals(original, recovered);
  }

  // ── TimeseriesDataPointIngestIO.toDataPoint() ────────────────────────────────

  @Test
  void ingestIO_toDataPoint_convertsTimestampToNs() {
    var ingest = new TimeseriesDataPointIngestIO("1970-01-01T00:00:01Z", 3.14);
    TimeseriesDataPoint dp = ingest.toDataPoint();
    assertEquals(1_000_000_000L, dp.getTimestamp());
    assertEquals(3.14, dp.getValue());
  }

  @Test
  void ingestIO_toDataPoint_invalidTimestamp_throwsBadRequest() {
    var ingest = new TimeseriesDataPointIngestIO("not-a-timestamp", 0);
    assertThrows(BadRequestException.class, ingest::toDataPoint);
  }
}
