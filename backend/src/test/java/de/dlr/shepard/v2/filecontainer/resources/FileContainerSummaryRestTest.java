package de.dlr.shepard.v2.filecontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * UI21-SIZEBAR-DATA — plain-Mockito unit tests for {@link FileContainerSummaryRest}.
 * Wires the resource by hand; no CDI or Neo4j required.
 */
class FileContainerSummaryRestTest {

  private static final long CONTAINER_ID = 17L;

  @Mock
  FileContainerService fileContainerService;

  FileContainerSummaryRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new FileContainerSummaryRest();
    resource.fileContainerService = fileContainerService;
  }

  private FileContainer containerWithFiles(int count) {
    FileContainer c = new FileContainer(CONTAINER_ID);
    for (int i = 0; i < count; i++) {
      c.addFile(new ShepardFile("oid-" + i, new Date(), "file-" + i + ".csv", null));
    }
    return c;
  }

  @Test
  void getSummary_returns200WithZeroFilesWhenContainerIsEmpty() {
    when(fileContainerService.getContainer(CONTAINER_ID)).thenReturn(containerWithFiles(0));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(0L, body.cardinality());
    assertNotNull(body.lastUpdated());
  }

  @Test
  void getSummary_returns200WithCorrectFileCount() {
    when(fileContainerService.getContainer(CONTAINER_ID)).thenReturn(containerWithFiles(5));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(5L, body.cardinality());
  }

  @Test
  void getSummary_returns200WithOneFile() {
    when(fileContainerService.getContainer(CONTAINER_ID)).thenReturn(containerWithFiles(1));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(1L, body.cardinality());
  }

  @Test
  void getSummary_handlesNullFilesListGracefully() {
    FileContainer c = new FileContainer(CONTAINER_ID);
    c.setFiles(null);
    when(fileContainerService.getContainer(CONTAINER_ID)).thenReturn(c);

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(0L, body.cardinality());
  }

  @Test
  void getSummary_callsPermissionCheckViaGetContainer() {
    when(fileContainerService.getContainer(CONTAINER_ID)).thenReturn(containerWithFiles(0));

    resource.getSummary(CONTAINER_ID);

    verify(fileContainerService).getContainer(CONTAINER_ID);
  }
}
