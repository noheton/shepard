package de.dlr.shepard.v2.filecontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.filecontainer.io.FileContainerStatsIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-STATS-NUMERIC-ID — unit tests for {@link FileContainerStatsRest}.
 *
 * <p>Verifies that the REST layer delegates to the service, resolves the appId
 * to an OGM id via {@link EntityIdResolver}, and returns the correct file count.
 */
class FileContainerStatsRestTest {

  private static final String APP_ID = "01928eaa-1111-7000-9000-aaaaaaaaaaaa";
  private static final long OGM_ID = 42L;

  private FileContainerService service;
  private EntityIdResolver resolver;
  private FileContainerStatsRest rest;

  @BeforeEach
  void setUp() {
    service = mock(FileContainerService.class);
    resolver = new EntityIdResolver();
    resolver.primeForTesting(OGM_ID, APP_ID);

    rest = new FileContainerStatsRest();
    rest.fileContainerService = service;
    rest.entityIdResolver = resolver;
  }

  @Test
  void returnsFileCountForContainer() {
    FileContainer container = mock(FileContainer.class);
    when(container.getFiles()).thenReturn(List.of(
        mock(ShepardFile.class), mock(ShepardFile.class), mock(ShepardFile.class)));
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    FileContainerStatsIO body = (FileContainerStatsIO) r.getEntity();
    assertEquals(3L, body.fileCount());
    verify(service).getContainer(OGM_ID);
  }

  @Test
  void returnsZeroWhenFilesListIsEmpty() {
    FileContainer container = mock(FileContainer.class);
    when(container.getFiles()).thenReturn(List.of());
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    FileContainerStatsIO body = (FileContainerStatsIO) r.getEntity();
    assertEquals(0L, body.fileCount());
  }

  @Test
  void returnsZeroWhenFilesListIsNull() {
    FileContainer container = mock(FileContainer.class);
    when(container.getFiles()).thenReturn(null);
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    FileContainerStatsIO body = (FileContainerStatsIO) r.getEntity();
    assertEquals(0L, body.fileCount());
  }
}
