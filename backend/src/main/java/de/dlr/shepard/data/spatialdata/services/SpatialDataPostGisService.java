package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.GeometryBuilder;
import de.dlr.shepard.data.spatialdata.model.KNearestNeighbor;
import de.dlr.shepard.data.spatialdata.model.SpatialGeometry;
import de.dlr.shepard.data.spatialdata.repositories.SpatialGeometryRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.NotImplementedError;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialDataPostGisService {

  @Inject
  private SpatialGeometryRepository spatialGeometryRepository;

  public void createSpatialDataPoints(Long containerId, List<SpatialDataPointIO> dataPoints) {
    final List<SpatialGeometry> spatialGeometryList = mapSpatialDataPointIO(containerId, dataPoints);
    spatialGeometryRepository.insertMultiple(containerId, spatialGeometryList.toArray(new SpatialGeometry[0]));
  }

  public List<SpatialDataPointIO> getSpatialDataPointIOs(int containerId, SpatialDataParamsIO spatialDataParamsIO) {
    switch (spatialDataParamsIO.getGeometryFilter().getType()) {
      case AXIS_ALIGNED_BOUNDING_BOX -> {
        return getByAABoundingBox(containerId, (AxisAlignedBoundingBox) spatialDataParamsIO.getGeometryFilter());
      }
      case BOUNDING_SPHERE -> {
        return getByBoundingSphere(containerId, (BoundingSphere) spatialDataParamsIO.getGeometryFilter());
      }
      case K_NEAREST_NEIGHBOR -> {
        return getByKNN(containerId, (KNearestNeighbor) spatialDataParamsIO.getGeometryFilter());
      }
      case ORIENTED_BOUNDING_BOX -> throw new NotImplementedError("not implemented");
      default -> throw new Error("Unknown geometry filter type"); //TODO: implement proper error type here or handle no-set geometry filter
    }
  }

  private List<SpatialDataPointIO> getByAABoundingBox(long containerId, AxisAlignedBoundingBox boundingBox) {
    final Coordinate bottomLeft = new Coordinate(boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ());
    final Coordinate topRight = new Coordinate(boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
    return mapSpatialGeometries(spatialGeometryRepository.getByBoundingBox(containerId, bottomLeft, topRight));
  }

  private List<SpatialDataPointIO> getByBoundingSphere(long containerId, BoundingSphere boundingSphere) {
    final Coordinate sphereCenter = new Coordinate(
      boundingSphere.getCenterX(),
      boundingSphere.getCenterY(),
      boundingSphere.getCenterZ()
    );
    return mapSpatialGeometries(
      spatialGeometryRepository.getByBoundingSphere(containerId, sphereCenter, boundingSphere.getR())
    );
  }

  private List<SpatialDataPointIO> getByKNN(long containerId, KNearestNeighbor knn) {
    final Coordinate kCoordinate = new Coordinate(knn.getX(), knn.getY(), knn.getZ());
    return mapSpatialGeometries(spatialGeometryRepository.getByKNN(containerId, kCoordinate, knn.getK()));
  }

  private List<SpatialDataPointIO> mapSpatialGeometries(List<SpatialGeometry> geometries) {
    return geometries
      .stream()
      .map(geometry ->
        new SpatialDataPointIO(
          geometry.getTime(),
          geometry.getGeometry().getCoordinate().getX(),
          geometry.getGeometry().getCoordinate().getY(),
          geometry.getGeometry().getCoordinate().getZ(),
          geometry.getMetadata(),
          geometry.getMeasurements()
        )
      )
      .collect(Collectors.toList());
  }

  private List<SpatialGeometry> mapSpatialDataPointIO(Long containerId, List<SpatialDataPointIO> dataPoints) {
    return dataPoints
      .stream()
      .map(point ->
        new SpatialGeometry(
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
