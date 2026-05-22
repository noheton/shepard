package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit coverage of {@link TsChannelResolver}'s tuple-projection
 * behaviour. The Panache JPA query paths are exercised via the existing
 * integration suite (TimescaleDB Testcontainers); these tests cover the
 * pieces of resolver logic that don't require a live database — null
 * handling, the 5-tuple round-trip, and shepardId pass-through.
 */
public class TsChannelResolverTest {

  /**
   * A subclass that lets us hand-construct rows and verify the
   * shepardId getter survives the resolver's projection helpers.
   * Resolution paths that call {@code find(…)} are integration-only
   * because Panache uses the static {@code QuarkusTransaction} context.
   */
  private static TimeseriesEntity rowWithShepardId(long containerId, UUID shepardId) {
    TimeseriesEntity row = new TimeseriesEntity(
      containerId,
      "vibration",
      "g_rms",
      "AFP-1",
      "head",
      "ts1",
      DataPointValueType.Double
    );
    row.setShepardId(shepardId);
    return row;
  }

  @Test
  void findByShepardId_returnsEmpty_whenInputIsNull() {
    TsChannelResolver resolver = new TsChannelResolver();
    Optional<TimeseriesEntity> result = resolver.findByShepardId(null);
    assertTrue(result.isEmpty(), "null shepardId must short-circuit to empty");
  }

  @Test
  void findByContainerAndTuple_returnsEmpty_whenTupleIsNull() {
    TsChannelResolver resolver = new TsChannelResolver();
    Optional<TimeseriesEntity> result = resolver.findByContainerAndTuple(1L, null);
    assertTrue(result.isEmpty(), "null tuple must short-circuit to empty");
  }

  @Test
  void timeseriesEntity_shepardId_roundtrip() {
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = rowWithShepardId(42L, id);
    assertNotNull(row.getShepardId(), "shepardId getter must return the set value");
    assertEquals(id, row.getShepardId());
  }

  @Test
  void timeseries_constructor_copiesTupleFromEntity() {
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = rowWithShepardId(42L, id);

    // Verify the Timeseries pojo's copy constructor reads back the 5-tuple.
    Timeseries tuple = new Timeseries(row);
    assertEquals("vibration", tuple.getMeasurement());
    assertEquals("g_rms", tuple.getField());
    assertEquals("AFP-1", tuple.getDevice());
    assertEquals("head", tuple.getLocation());
    assertEquals("ts1", tuple.getSymbolicName());
  }

  @Test
  void timeseriesEntity_uniqueId_excludesShepardId() {
    // The existing legacy uniqueId is the 5-tuple + valueType — shepardId
    // is a NEW column that must not perturb existing lookups built on
    // this string key. Lock the contract.
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = rowWithShepardId(42L, id);
    String legacyKey = row.getUniqueId();
    assertTrue(
      !legacyKey.contains(id.toString()),
      "legacy uniqueId must not embed shepardId — would silently invalidate existing string-keyed caches"
    );
    assertEquals("vibration-AFP-1-head-ts1-g_rms-Double", legacyKey);
  }
}
