package de.dlr.shepard.v2.importer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * IMP-STATS-01 — unit tests for {@link ImportStatsCollector}.
 *
 * <p>All Neo4j and JDBC I/O is mocked.  Tests cover:
 * <ul>
 *   <li>Bootstrap: find-or-create container node paths.</li>
 *   <li>collect() skips when bootstrap failed (containerNeo4jId == -1).</li>
 *   <li>collect() handles unexpected exceptions without re-throwing.</li>
 *   <li>Channel constant sanity check.</li>
 * </ul>
 *
 * <p>The Neo4j {@code Session} is accessed via package-visible metric methods
 * ({@link ImportStatsCollector#countGlobalDataObjects()},
 * {@link ImportStatsCollector#sumFileSizeBytes()}) which are stubbed out on a
 * Mockito spy — avoiding the need to reach into the protected {@code session}
 * field on {@code GenericDAO}.
 */
class ImportStatsCollectorTest {

  static final long CONTAINER_ID = 42L;

  @Mock
  TimeseriesContainerDAO containerDAO;

  @Mock
  ImportPlanDAO importPlanDAO;

  @Mock
  TimeseriesRepository timeseriesRepository;

  @Mock
  AgroalDataSource defaultDataSource;

  @Mock
  Connection connection;

  @Mock
  PreparedStatement preparedStatement;

  /** Spy so we can stub package-visible metric methods without touching Neo4j. */
  ImportStatsCollector collector;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    ImportStatsCollector real = new ImportStatsCollector();
    real.containerDAO        = containerDAO;
    real.importPlanDAO       = importPlanDAO;
    real.timeseriesRepository = timeseriesRepository;
    real.defaultDataSource   = defaultDataSource;

    collector = spy(real);

    // Default JDBC stubs — succeed silently.
    when(defaultDataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeUpdate()).thenReturn(1);
  }

  // ─── Bootstrap: find-or-create ────────────────────────────────────────────

  @Test
  void bootstrap_usesExistingContainerWhenPresent() {
    TimeseriesContainer existing = containerWithId(CONTAINER_ID);
    when(containerDAO.findByAppId(ImportStatsCollector.CONTAINER_APP_ID))
        .thenReturn(Optional.of(existing));

    long id = collector.findOrCreateContainerForTest();

    assertEquals(CONTAINER_ID, id);
    verify(containerDAO, never()).createOrUpdate(any());
  }

  @Test
  void bootstrap_createsContainerWhenAbsent() {
    when(containerDAO.findByAppId(ImportStatsCollector.CONTAINER_APP_ID))
        .thenReturn(Optional.empty());

    TimeseriesContainer created = containerWithId(CONTAINER_ID);
    when(containerDAO.createOrUpdate(any())).thenReturn(created);

    long id = collector.findOrCreateContainerForTest();

    assertEquals(CONTAINER_ID, id);
    verify(containerDAO).createOrUpdate(any(TimeseriesContainer.class));
  }

  // ─── collect() guard: skip when not bootstrapped ─────────────────────────

  @Test
  void collect_skipsWhenNotBootstrapped() throws Exception {
    // containerNeo4jId stays at default -1 (never bootstrapped)
    collector.collect();

    verify(timeseriesRepository, never()).upsert(anyLong(), any());
    verify(defaultDataSource, never()).getConnection();
  }

  // ─── collect() happy path ─────────────────────────────────────────────────

  @Test
  void collect_writesDataPointsWhenBootstrapped() throws Exception {
    collector.containerNeo4jId.set(CONTAINER_ID);

    // Stub metric queries through the DAO mocks.
    when(importPlanDAO.countAllDataObjects()).thenReturn(100L);
    when(importPlanDAO.sumFileSizeBytes()).thenReturn(2048L);

    // Stub ensureChannel round-trip: upsert is a void, findTimeseries returns entity.
    TimeseriesEntity fakeChannel = buildFakeChannel(CONTAINER_ID, 7);
    when(timeseriesRepository.findTimeseries(anyLong(), any())).thenReturn(Optional.of(fakeChannel));

    collector.collect();

    // Two data-point writes — one per channel.
    verify(defaultDataSource, org.mockito.Mockito.times(2)).getConnection();
  }

  // ─── collect() error resilience ───────────────────────────────────────────

  @Test
  void collect_doesNotRethrowOnException() throws Exception {
    collector.containerNeo4jId.set(CONTAINER_ID);

    // Make the DO-count query blow up via the DAO mock.
    when(importPlanDAO.countAllDataObjects()).thenThrow(new RuntimeException("Neo4j down"));

    // Must not propagate — Quarkus scheduler must keep running.
    collector.collect();
  }

  // ─── Channel identity constants ───────────────────────────────────────────

  @Test
  void channelConstants_haveExpectedValues() {
    assertEquals("import",           ImportStatsCollector.MEASUREMENT);
    assertEquals("shepard-backend",  ImportStatsCollector.DEVICE);
    assertEquals("global",           ImportStatsCollector.LOCATION);
    assertEquals("value",            ImportStatsCollector.FIELD);
    assertEquals("data_objects_created", ImportStatsCollector.CHANNEL_DATA_OBJECTS);
    assertEquals("bytes_stored",         ImportStatsCollector.CHANNEL_BYTES_STORED);
  }

  @Test
  void containerAppId_isExpectedString() {
    assertEquals("shepard-import-stats", ImportStatsCollector.CONTAINER_APP_ID);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private static TimeseriesContainer containerWithId(long id) {
    return new TimeseriesContainer(id);
  }

  private static TimeseriesEntity buildFakeChannel(long containerId, int id) {
    TimeseriesEntity e = new TimeseriesEntity(
      containerId,
      ImportStatsCollector.MEASUREMENT,
      ImportStatsCollector.FIELD,
      ImportStatsCollector.DEVICE,
      ImportStatsCollector.LOCATION,
      ImportStatsCollector.CHANNEL_DATA_OBJECTS,
      DataPointValueType.Integer
    );
    // Reflectively set the generated id so getId() returns something non-zero.
    try {
      var f = TimeseriesEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(e, id);
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError("Cannot set TimeseriesEntity.id for test", ex);
    }
    return e;
  }
}
