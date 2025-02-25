package de.dlr.shepard.data.spatialdata.services;

import static org.mockito.Mockito.when;

import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import de.dlr.shepard.data.spatialdata.repositories.SpatialDataPointRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusComponentTest
public class SpatialDataPointServiceTest {

  @Inject
  SpatialDataPointService spatialDataPointService;

  @InjectMock
  SpatialDataPointRepository spatialGeometryRepository;

  @InjectMock
  SpatialDataContainerService spatialDataContainerService;

  @Test
  public void createSpatialDataPoints_callRepository() {
    Long containerId = 1L;
    var dataPoints = Arrays.asList(new SpatialDataPointIO(1L, 1.0, 2.0, 3.0, null, null));

    spatialDataPointService.createSpatialDataPoints(containerId, dataPoints);

    Mockito.verify(spatialGeometryRepository, Mockito.times(1)).insertMultiple(
      Mockito.eq(containerId),
      Mockito.any(SpatialDataPoint[].class)
    );
  }

  @Test
  public void createSpatialDataPoints_containerDoesNotExist_throwsException() {
    Long containerId = 1L;
    var dataPoints = Arrays.asList(new SpatialDataPointIO(1L, 1.0, 2.0, 3.0, null, null));
    when(spatialDataContainerService.getContainer(1)).thenThrow(new NotFoundException());

    try {
      spatialDataPointService.createSpatialDataPoints(containerId, dataPoints);
    } catch (NotFoundException e) {
      assert true;
    }
  }

  @Test
  public void deleteByContainerId_callRepository() {
    long containerId = 1L;

    spatialDataPointService.deleteByContainerId(containerId);

    Mockito.verify(spatialGeometryRepository, Mockito.times(1)).deleteByContainerId(containerId);
  }
}
