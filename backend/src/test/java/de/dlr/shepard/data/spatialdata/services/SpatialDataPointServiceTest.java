package de.dlr.shepard.data.spatialdata.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.GeometryFilterType;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import de.dlr.shepard.data.spatialdata.repositories.SpatialDataPointRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Arrays;
import java.util.Map;
import kotlin.NotImplementedError;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
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

    Mockito.verify(spatialGeometryRepository, Mockito.times(1)).insert(
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

  @Test
  public void getSpatialDataPointIOs_boundingBoxFilter_callRepository() {
    Long containerId = 1L;
    Long startTime = 2L, endTime = 3L;
    Integer limit = 4;
    Map<String, Object> metadataFilter = Map.of("data", "some data");

    AxisAlignedBoundingBox axisAlignedBoundingBox = new AxisAlignedBoundingBox(0.1, 0.2, 0.3, 1.1, 1.2, 1.3);
    SpatialDataQueryParams spatialDataParamsIO = new SpatialDataQueryParams(
      axisAlignedBoundingBox,
      metadataFilter,
      null,
      startTime,
      endTime,
      limit,
      null
    );
    spatialDataPointService.getSpatialDataPointIOs(containerId, spatialDataParamsIO);
    verify(spatialGeometryRepository, times(1)).getByBoundingBox(
      eq(containerId),
      eq(
        new Coordinate(
          axisAlignedBoundingBox.getMinX(),
          axisAlignedBoundingBox.getMinY(),
          axisAlignedBoundingBox.getMinZ()
        )
      ),
      eq(
        new Coordinate(
          axisAlignedBoundingBox.getMaxX(),
          axisAlignedBoundingBox.getMaxY(),
          axisAlignedBoundingBox.getMaxZ()
        )
      ),
      eq(startTime),
      eq(endTime),
      eq(metadataFilter),
      any(),
      eq(limit),
      eq(null)
    );
  }

  @Test
  public void getSpatialDataPointIOs_boundingSphereFilter_callRepository() {
    Long containerId = 1L;
    Long startTime = 2L, endTime = 3L;
    Integer limit = 4;
    Map<String, Object> metadataFilter = Map.of("data", "some data");
    double radius = 10;

    BoundingSphere boundingSphere = new BoundingSphere(radius, 1.1, 1.2, 1.3);

    SpatialDataQueryParams spatialDataParamsIO = new SpatialDataQueryParams(
      boundingSphere,
      metadataFilter,
      null,
      startTime,
      endTime,
      limit,
      null
    );
    spatialDataPointService.getSpatialDataPointIOs(containerId, spatialDataParamsIO);
    verify(spatialGeometryRepository, times(1)).getByBoundingSphere(
      eq(containerId),
      eq(new Coordinate(boundingSphere.getCenterX(), boundingSphere.getCenterY(), boundingSphere.getCenterZ())),
      eq(radius),
      eq(startTime),
      eq(endTime),
      eq(metadataFilter),
      any(),
      eq(limit),
      eq(null)
    );
  }

  @Test
  public void getSpatialDataPointIOs_knnFilter_callRepository() {
    Long containerId = 1L;
    Long startTime = 2L, endTime = 3L;
    Integer limit = 4;
    Map<String, Object> metadataFilter = Map.of("data", "some data");
    int k = 10;

    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor(k, 1.1, 1.2, 1.3);

    SpatialDataQueryParams spatialDataParamsIO = new SpatialDataQueryParams(
      kNearestNeighbor,
      metadataFilter,
      null,
      startTime,
      endTime,
      limit,
      null
    );
    spatialDataPointService.getSpatialDataPointIOs(containerId, spatialDataParamsIO);
    verify(spatialGeometryRepository, times(1)).getByKNN(
      eq(containerId),
      eq(new Coordinate(kNearestNeighbor.getX(), kNearestNeighbor.getY(), kNearestNeighbor.getZ())),
      eq(k),
      eq(startTime),
      eq(endTime),
      eq(metadataFilter),
      any()
    );
  }

  @Test
  public void getSpatialDataPointIOs_orientedBoundingBox_throwsNotImplemented() {
    Long containerId = 1L;
    Long startTime = 2L, endTime = 3L;
    Integer limit = 4;
    Map<String, Object> metadataFilter = Map.of("data", "some data");
    AbstractGeometryFilter geometryFilter = new AbstractGeometryFilter() {
      public boolean isValid() {
        return true;
      }
    };
    geometryFilter.setType(GeometryFilterType.ORIENTED_BOUNDING_BOX);

    SpatialDataQueryParams spatialDataParamsIO = new SpatialDataQueryParams(
      geometryFilter,
      metadataFilter,
      null,
      startTime,
      endTime,
      limit,
      null
    );

    Error error = assertThrows(NotImplementedError.class, () ->
      spatialDataPointService.getSpatialDataPointIOs(containerId, spatialDataParamsIO)
    );
    assertEquals(error.getMessage(), "not implemented");
  }
}
