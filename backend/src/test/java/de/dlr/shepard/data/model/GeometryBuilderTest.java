package de.dlr.shepard.data.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.data.spatialdata.model.GeometryBuilder;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

public class GeometryBuilderTest {

  @Test
  public void buildFromXYZ_GeometryIsAPointWithZCoordinate() {
    var actual = GeometryBuilder.fromXYZ(1, 2, 3);

    assertEquals(1, actual.getCoordinate().x);
    assertEquals(2, actual.getCoordinate().y);
    assertEquals(3, actual.getCoordinate().z);
  }

  @Test
  public void buildFromCoordinate_GeometryIsAPointWithZCoordinate() {
    var actual = GeometryBuilder.fromCoordinate(new Coordinate(1, 2, 3));

    assertEquals(1, actual.getCoordinate().x);
    assertEquals(2, actual.getCoordinate().y);
    assertEquals(3, actual.getCoordinate().z);
  }
}
