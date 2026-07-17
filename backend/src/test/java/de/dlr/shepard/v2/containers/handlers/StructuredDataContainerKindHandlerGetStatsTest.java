package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.daos.StructuredDataDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-CONTAINER-STATS-OGM-COUNT — unit tests for
 * {@link StructuredDataContainerKindHandler#getStats(String)}.
 *
 * <p>Verifies that the handler returns {@code entryCount} via the Cypher DAO (not OGM
 * lazy-load) and that kind-specific timeseries fields are null.
 */
class StructuredDataContainerKindHandlerGetStatsTest {

  private static final String APP_ID = "01928eaa-2222-7000-9000-bbbbbbbbbbbb";

  private StructuredDataContainerKindHandler handler;
  private StructuredDataContainerDAO daoMock;
  private StructuredDataDAO structuredDataDAOMock;

  @BeforeEach
  void setUp() throws Exception {
    handler = new StructuredDataContainerKindHandler();
    daoMock = mock(StructuredDataContainerDAO.class);
    structuredDataDAOMock = mock(StructuredDataDAO.class);
    inject(handler, "dao", daoMock);
    inject(handler, "structuredDataDAO", structuredDataDAOMock);
  }

  @Test
  void returnsEmptyWhenContainerNotFound() {
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.empty());

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void returnsEmptyWhenContainerIsDeleted() {
    StructuredDataContainer c = mock(StructuredDataContainer.class);
    when(c.isDeleted()).thenReturn(true);
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void returnsEntryCount() {
    StructuredDataContainer c = mock(StructuredDataContainer.class);
    when(c.isDeleted()).thenReturn(false);
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));
    when(structuredDataDAOMock.countByContainerAppId(APP_ID)).thenReturn(2L);

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    ContainerStatsIO stats = result.get();
    assertEquals(2L, stats.entryCount());
    // timeseries-specific and file-specific fields must be null
    assertNull(stats.pointCount());
    assertNull(stats.fileCount());
  }

  @Test
  void returnsZeroCount() {
    StructuredDataContainer c = mock(StructuredDataContainer.class);
    when(c.isDeleted()).thenReturn(false);
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));
    when(structuredDataDAOMock.countByContainerAppId(APP_ID)).thenReturn(0L);

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    assertEquals(0L, result.get().entryCount());
  }

  // ── reflection helper ──────────────────────────────────────────────────

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException("No field '" + fieldName + "' on " + target.getClass());
  }
}
