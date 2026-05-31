package de.dlr.shepard.v2.filecontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalityIO;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link FileContainerSummaryRest} (UI21-SIZEBAR-DATA).
 */
class FileContainerSummaryRestTest {

  static final long CONTAINER_ID = 7L;

  @Mock FileContainerService service;

  FileContainerSummaryRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new FileContainerSummaryRest();
    resource.fileContainerService = service;
  }

  @Test
  void getSummary_returnsFilesCount() {
    FileContainer container = new FileContainer(CONTAINER_ID);
    container.addFile(new ShepardFile());
    container.addFile(new ShepardFile());
    container.addFile(new ShepardFile());
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(3);
  }

  @Test
  void getSummary_returnsZeroForEmptyContainer() {
    FileContainer container = new FileContainer(CONTAINER_ID);
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(0);
  }

  @Test
  void getSummary_returnsZeroWhenFilesListIsNull() {
    FileContainer container = new FileContainer(CONTAINER_ID);
    container.setFiles(null);
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(0);
  }

  @Test
  void getSummary_propagates404WhenContainerNotFound() {
    when(service.getContainer(CONTAINER_ID)).thenThrow(new NotFoundException("not found"));

    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> resource.getSummary(CONTAINER_ID)
    );
  }

  @Test
  void getSummary_withManyFiles() {
    FileContainer container = new FileContainer(CONTAINER_ID);
    for (int i = 0; i < 50; i++) {
      container.addFile(new ShepardFile());
    }
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(50);
  }
}
