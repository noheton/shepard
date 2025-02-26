package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.GeometryBuilder;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import de.dlr.shepard.data.spatialdata.repositories.SpatialDataPointRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.NotImplementedError;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialDataPointService {

  private SpatialDataPointRepository spatialDataPointRepository;
  private SpatialDataContainerService spatialDataContainerService;

  @Inject
  public SpatialDataPointService(
    SpatialDataPointRepository spatialGeometryRepository,
    SpatialDataContainerService spatialDataContainerService
  ) {
    this.spatialDataPointRepository = spatialGeometryRepository;
    this.spatialDataContainerService = spatialDataContainerService;
  }

  SpatialDataPointService() {}

  @Transactional
  public void createSpatialDataPoints(Long containerId, List<SpatialDataPointIO> dataPoints) {
    spatialDataContainerService.getContainer(containerId);

    final List<SpatialDataPoint> spatialGeometryList = mapSpatialDataPointIO(containerId, dataPoints);
    spatialDataPointRepository.insertMultiple(containerId, spatialGeometryList.toArray(new SpatialDataPoint[0]));
  }

  public List<SpatialDataPointIO> getSpatialDataPointIOs(long containerId, SpatialDataParamsIO spatialDataParamsIO) {
    switch (spatialDataParamsIO.getGeometryFilter().getType()) {
      case AXIS_ALIGNED_BOUNDING_BOX -> {
        return getByAABoundingBox(
          containerId,
          (AxisAlignedBoundingBox) spatialDataParamsIO.getGeometryFilter(),
          spatialDataParamsIO
        );
      }
      case BOUNDING_SPHERE -> {
        return getByBoundingSphere(
          containerId,
          (BoundingSphere) spatialDataParamsIO.getGeometryFilter(),
          spatialDataParamsIO
        );
      }
      case K_NEAREST_NEIGHBOR -> {
        return getByKNN(containerId, (KNearestNeighbor) spatialDataParamsIO.getGeometryFilter(), spatialDataParamsIO);
      }
      case ORIENTED_BOUNDING_BOX -> throw new NotImplementedError("not implemented");
      default -> throw new Error("Unknown geometry filter type");
    }
  }

  @Transactional
  public void deleteByContainerId(long containerId) {
    spatialDataPointRepository.deleteByContainerId(containerId);
  }

  private List<SpatialDataPointIO> getByAABoundingBox(
    long containerId,
    AxisAlignedBoundingBox boundingBox,
    SpatialDataParamsIO spatialDataParamsIO
  ) {
    final Coordinate bottomLeft = new Coordinate(boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ());
    final Coordinate topRight = new Coordinate(boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
    return mapSpatialDataPoints(
      spatialDataPointRepository.getByBoundingBox(
        containerId,
        bottomLeft,
        topRight,
        spatialDataParamsIO.getStartTime(),
        spatialDataParamsIO.getEndTime(),
        spatialDataParamsIO.getMetadata(),
        spatialDataParamsIO.getLimit()
      )
    );
  }

  private List<SpatialDataPointIO> getByBoundingSphere(
    long containerId,
    BoundingSphere boundingSphere,
    SpatialDataParamsIO spatialDataParamsIO
  ) {
    final Coordinate sphereCenter = new Coordinate(
      boundingSphere.getCenterX(),
      boundingSphere.getCenterY(),
      boundingSphere.getCenterZ()
    );

    return mapSpatialDataPoints(
      spatialDataPointRepository.getByBoundingSphere(
        containerId,
        sphereCenter,
        boundingSphere.getRadius(),
        spatialDataParamsIO.getStartTime(),
        spatialDataParamsIO.getEndTime(),
        spatialDataParamsIO.getMetadata(),
        spatialDataParamsIO.getLimit()
      )
    );
  }

  private List<SpatialDataPointIO> getByKNN(
    long containerId,
    KNearestNeighbor knn,
    SpatialDataParamsIO spatialDataParamsIO
  ) {
    final Coordinate kCoordinate = new Coordinate(knn.getX(), knn.getY(), knn.getZ());
    return mapSpatialDataPoints(
      spatialDataPointRepository.getByKNN(
        containerId,
        kCoordinate,
        knn.getK(),
        spatialDataParamsIO.getStartTime(),
        spatialDataParamsIO.getEndTime(),
        spatialDataParamsIO.getMetadata()
      )
    );
  }

  private List<SpatialDataPointIO> mapSpatialDataPoints(List<SpatialDataPoint> dataPoints) {
    return dataPoints
      .stream()
      .map(point ->
        new SpatialDataPointIO(
          point.getTime(),
          point.getPosition().getCoordinate().getX(),
          point.getPosition().getCoordinate().getY(),
          point.getPosition().getCoordinate().getZ(),
          point.getMeasurements(),
          point.getMetadata()
        )
      )
      .collect(Collectors.toList());
  }

  private List<SpatialDataPoint> mapSpatialDataPointIO(Long containerId, List<SpatialDataPointIO> dataPoints) {
    return dataPoints
      .stream()
      .map(point ->
        new SpatialDataPoint(
          containerId,
          point.getTimestamp(),
          GeometryBuilder.fromXYZ(point.getX(), point.getY(), point.getZ()),
          point.getMetadata(),
          point.getMeasurements()
        )
      )
      .collect(Collectors.toList());
  }
}
