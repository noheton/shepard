package de.dlr.shepard.data.spatialdata.services;

import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.io.SpatialDataParamsIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.KNearestNeighbor;
import de.dlr.shepard.data.spatialdata.modelpgvector.PGVectorSpatialDataPoint;
import de.dlr.shepard.data.spatialdata.repositories.PGVectorSpatialDataPointRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import kotlin.NotImplementedError;

@RequestScoped
public class PGVectorSpatialDataService {

  private PGVectorSpatialDataPointRepository pgVectorSpatialDataPointRepository;

  @Inject
  public PGVectorSpatialDataService(PGVectorSpatialDataPointRepository pointRepository) {
    this.pgVectorSpatialDataPointRepository = pointRepository;
  }

  public List<SpatialDataPointIO> getSpatialDataPoints(long containerId, SpatialDataParamsIO spatialDataParams) {
    return mapPGVectorPoints(getPGVectorPoints(containerId, spatialDataParams));
  }

  @Transactional
  public void createSpatialDataPoints(long containerId, List<SpatialDataPointIO> dataPoints) {
    pgVectorSpatialDataPointRepository.insert(mapSpatialDataPointIO(containerId, dataPoints));
  }

  private List<PGVectorSpatialDataPoint> getPGVectorPoints(long containerId, SpatialDataParamsIO spatialDataParams) {
    switch (spatialDataParams.getGeometryFilter().getType()) {
      case AXIS_ALIGNED_BOUNDING_BOX:
        AxisAlignedBoundingBox alignedBoundingBox = ((AxisAlignedBoundingBox) spatialDataParams.getGeometryFilter());
        return pgVectorSpatialDataPointRepository.findWithinAxisAlignedBoundingBox(
          (float) alignedBoundingBox.getMinX(),
          (float) alignedBoundingBox.getMinY(),
          (float) alignedBoundingBox.getMinZ(),
          (float) alignedBoundingBox.getMaxX(),
          (float) alignedBoundingBox.getMaxY(),
          (float) alignedBoundingBox.getMaxZ(),
          containerId,
          spatialDataParams.getStartTime(),
          spatialDataParams.getEndTime(),
          spatialDataParams.getMetadata()
        );
      case K_NEAREST_NEIGHBOR:
        KNearestNeighbor kNearestNeighbor = ((KNearestNeighbor) spatialDataParams.getGeometryFilter());
        return pgVectorSpatialDataPointRepository.findKNearest(
          (float) kNearestNeighbor.getX(),
          (float) kNearestNeighbor.getY(),
          (float) kNearestNeighbor.getZ(),
          kNearestNeighbor.getK(),
          containerId,
          spatialDataParams.getStartTime(),
          spatialDataParams.getEndTime(),
          spatialDataParams.getMetadata()
        );
      case BOUNDING_SPHERE:
        BoundingSphere boundingSphere = ((BoundingSphere) spatialDataParams.getGeometryFilter());
        return pgVectorSpatialDataPointRepository.findWithinSphere(
          (float) boundingSphere.getR(),
          (float) boundingSphere.getCenterX(),
          (float) boundingSphere.getCenterY(),
          (float) boundingSphere.getCenterZ(),
          containerId,
          spatialDataParams.getStartTime(),
          spatialDataParams.getEndTime(),
          spatialDataParams.getMetadata()
        );
      case ORIENTED_BOUNDING_BOX:
        throw new NotImplementedError("Oriented bounding box filter not implemented");
      default:
        throw new NotImplementedError("Not supported filter");
    }
  }

  List<SpatialDataPointIO> mapPGVectorPoints(List<PGVectorSpatialDataPoint> pgVectorPoints) {
    return pgVectorPoints
      .stream()
      .map(point ->
        new SpatialDataPointIO(
          point.getTimestamp(),
          (double) point.getPoint()[0],
          (double) point.getPoint()[1],
          (double) point.getPoint()[2],
          (Map<String, Object>) JsonConverter.convertToObject(point.getMeasurements()),
          (Map<String, Object>) JsonConverter.convertToObject(point.getMetadata())
        )
      )
      .toList();
  }

  List<PGVectorSpatialDataPoint> mapSpatialDataPointIO(long containerId, List<SpatialDataPointIO> pointIOs) {
    return pointIOs
      .stream()
      .map(point ->
        new PGVectorSpatialDataPoint(
          containerId,
          point.getTimestamp(),
          new float[] { point.getX().floatValue(), point.getY().floatValue(), point.getZ().floatValue() },
          JsonConverter.convertToString(point.getMeasurements()),
          JsonConverter.convertToString(point.getMetadata())
        )
      )
      .toList();
  }
}
