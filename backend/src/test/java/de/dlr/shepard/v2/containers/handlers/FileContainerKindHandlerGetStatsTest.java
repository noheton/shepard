package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-FILE-CONTAINER-STATS-UNIFY — unit tests for
 * {@link FileContainerKindHandler#getStats(String)}.
 *
 * <p>Verifies that the handler returns {@code fileCount} in the {@link ContainerStatsIO}
 * and that kind-specific timeseries fields are null (omitted from JSON via
 * {@code @JsonInclude(NON_NULL)}).
 */
class FileContainerKindHandlerGetStatsTest {

  private static final String APP_ID = "01928eaa-1111-7000-9000-aaaaaaaaaaaa";

  private FileContainerKindHandler handler;
  private FileContainerDAO daoMock;

  @BeforeEach
  void setUp() throws Exception {
    handler = new FileContainerKindHandler();
    daoMock = mock(FileContainerDAO.class);
    inject(handler, "dao", daoMock);
  }

  @Test
  void returnsEmptyWhenContainerNotFound() {
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.empty());

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void returnsEmptyWhenContainerIsDeleted() {
    FileContainer c = mock(FileContainer.class);
    when(c.isDeleted()).thenReturn(true);
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void returnsFileCount() {
    FileContainer c = mock(FileContainer.class);
    when(c.isDeleted()).thenReturn(false);
    when(c.getFiles()).thenReturn(List.of(
        mock(ShepardFile.class), mock(ShepardFile.class), mock(ShepardFile.class)));
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    ContainerStatsIO stats = result.get();
    assertEquals(3L, stats.fileCount());
    // timeseries-specific fields must be null
    assertTrue(stats.pointCount() == null);
    assertTrue(stats.channelCount() == null);
    assertTrue(stats.entryCount() == null);
  }

  @Test
  void returnsZeroWhenFilesListIsEmpty() {
    FileContainer c = mock(FileContainer.class);
    when(c.isDeleted()).thenReturn(false);
    when(c.getFiles()).thenReturn(List.of());
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    assertEquals(0L, result.get().fileCount());
  }

  @Test
  void returnsZeroWhenFilesListIsNull() {
    FileContainer c = mock(FileContainer.class);
    when(c.isDeleted()).thenReturn(false);
    when(c.getFiles()).thenReturn(null);
    when(daoMock.findByAppId(APP_ID)).thenReturn(Optional.of(c));

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    assertEquals(0L, result.get().fileCount());
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
