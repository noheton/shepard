package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalityIO;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link StructuredDataContainerSummaryRest} (UI21-SIZEBAR-DATA).
 */
class StructuredDataContainerSummaryRestTest {

  static final long CONTAINER_ID = 12L;

  @Mock StructuredDataContainerService service;

  StructuredDataContainerSummaryRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new StructuredDataContainerSummaryRest();
    resource.structuredDataContainerService = service;
  }

  @Test
  void getSummary_returnsRecordCount() {
    StructuredDataContainer container = new StructuredDataContainer(CONTAINER_ID);
    container.addStructuredData(new StructuredData("entry1", new Date()));
    container.addStructuredData(new StructuredData("entry2", new Date()));
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(2);
  }

  @Test
  void getSummary_returnsZeroForEmptyContainer() {
    StructuredDataContainer container = new StructuredDataContainer(CONTAINER_ID);
    when(service.getContainer(CONTAINER_ID)).thenReturn(container);

    var r = resource.getSummary(CONTAINER_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    ContainerCardinalityIO body = (ContainerCardinalityIO) r.getEntity();
    assertThat(body.cardinality()).isEqualTo(0);
  }

  @Test
  void getSummary_returnsZeroWhenListIsNull() {
    StructuredDataContainer container = new StructuredDataContainer(CONTAINER_ID);
    container.setStructuredDatas(null);
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
}
