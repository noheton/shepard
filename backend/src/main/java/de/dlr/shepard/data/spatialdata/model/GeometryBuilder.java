package de.dlr.shepard.data.spatialdata.model;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

public final class GeometryBuilder {

  // Todo: no precision model defined yet
  public static GeometryFactory factory = new GeometryFactory();

  public static Geometry fromCoordinate(Coordinate coordinate) {
    return factory.createPoint(coordinate);
  }

  public static Geometry fromCoordinates(Coordinate[] coordinates) {
    return factory.createMultiPointFromCoords(coordinates);
  }

  public static Geometry fromXYZ(double x, double y, double z) {
    return fromCoordinate(new Coordinate(x, y, z));
  }
}
