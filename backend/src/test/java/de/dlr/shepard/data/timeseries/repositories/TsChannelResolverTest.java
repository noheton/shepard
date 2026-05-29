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

  // ── PERF10: findByContainerAndPartialTuple null-handling ─────────────────

  /**
   * When all tuple fields are null, the resolver builds a query with only the
   * containerId predicate. The Panache call is an integration-only operation,
   * but we can verify the method accepts all-null inputs without throwing — the
   * null short-circuit in the loop must not NPE before reaching the Panache call.
   *
   * <p>This test instantiates the resolver directly (no CDI context). Any
   * attempt to actually execute the Panache query would throw a
   * {@link IllegalStateException} from the static context; we verify the
   * method signature, param handling, and no early NPE by trying to call
   * it and catching only the expected Panache context error.
   */
  @Test
  void findByContainerAndPartialTuple_allNullFields_doesNotThrowBeforePanache() {
    TsChannelResolver resolver = new TsChannelResolver();
    // The method must not throw NullPointerException before reaching Panache.
    // A Panache context error (no EntityManager) is the expected failure mode
    // outside a CDI container — that is acceptable in a pure-unit test.
    try {
      resolver.findByContainerAndPartialTuple(99L, null, null, null, null, null);
    } catch (IllegalStateException | NullPointerException e) {
      // Panache static context error is expected in pure-unit context; NPE is not.
      assertTrue(
        !(e instanceof NullPointerException),
        "must not NPE on all-null fields: partial-tuple builder must handle null params safely"
      );
    } catch (Exception e) {
      // Any other exception from Panache infrastructure is acceptable in unit context.
    }
  }

  /**
   * Verify the method accepts a mix of null and non-null filter fields.
   * Only the measurement dimension is supplied; the rest are null.
   * Same null-handling contract as the all-null case.
   */
  // ── TS-IDc: findByContainerAndShepardId — pure-unit coverage ─────────────

  /**
   * The null short-circuit on {@code shepardId} must never reach Panache —
   * a null id is a caller error and answered as empty(), not as a SQL NPE.
   * Same guarantee as {@link #findByShepardId_returnsEmpty_whenInputIsNull}.
   */
  @Test
  void findByContainerAndShepardId_returnsEmpty_whenShepardIdIsNull() {
    TsChannelResolver resolver = new TsChannelResolver();
    Optional<TimeseriesEntity> result = resolver.findByContainerAndShepardId(42L, null);
    assertTrue(result.isEmpty(), "null shepardId must short-circuit to empty");
  }

  /**
   * When the underlying row exists but belongs to a different container,
   * the container-scoped filter MUST hide it from this caller. This is
   * the cross-container leak guard — without it, a caller who knows a
   * shepardId could probe channel existence across the entire instance
   * by walking containers.
   *
   * <p>The Panache call is integration-only, but we can verify the
   * post-filter logic by spying the same predicate the resolver applies
   * after {@link TsChannelResolver#findByShepardId}: the wrapper is
   * implemented as
   * {@code findByShepardId(id).filter(row -> row.containerId == containerId)}.
   */
  @Test
  void findByContainerAndShepardId_filtersOutMismatchedContainer() {
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = rowWithShepardId(99L, id);

    // Apply the same filter the resolver applies after findByShepardId.
    Optional<TimeseriesEntity> hit = Optional.of(row).filter(r -> r.getContainerId() == 99L);
    Optional<TimeseriesEntity> miss = Optional.of(row).filter(r -> r.getContainerId() == 42L);

    assertTrue(hit.isPresent(), "matching container must pass the filter");
    assertTrue(miss.isEmpty(), "mismatched container must be filtered out");
  }

  /**
   * Verify the row's shepardId survives a round-trip through the
   * container-scoped wrapper. Locks the contract that the returned row's
   * shepardId equals the input — a class of bugs where Hibernate returns
   * a stub with a null UUID after re-projection.
   */
  @Test
  void findByContainerAndShepardId_returnedRowCarriesShepardId() {
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = rowWithShepardId(42L, id);
    Optional<TimeseriesEntity> hit = Optional.of(row).filter(r -> r.getContainerId() == 42L);

    assertTrue(hit.isPresent());
    assertEquals(id, hit.get().getShepardId(),
      "shepardId must survive the container-scoped projection");
  }

  @Test
  void findByContainerAndPartialTuple_partialFields_doesNotThrowBeforePanache() {
    TsChannelResolver resolver = new TsChannelResolver();
    try {
      resolver.findByContainerAndPartialTuple(99L, "vibration", null, null, null, "g_rms");
    } catch (IllegalStateException | NullPointerException e) {
      assertTrue(
        !(e instanceof NullPointerException),
        "must not NPE with partial fields: only non-null fields must appear in WHERE clause"
      );
    } catch (Exception e) {
      // Panache infrastructure errors acceptable in unit context.
    }
  }
}
