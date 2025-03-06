package de.dlr.shepard.context.references.spatialdatareference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.io.Operator;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import java.util.Date;
import java.util.List;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class SpatialDataReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(SpatialDataReferenceIO.class).verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("arthur");
    var update = new Date();
    var updateUser = new User("dutch");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(500L);
    var container = new SpatialDataContainer(3L);
    var geometryFilter = new KNearestNeighbor(5, 10.0, 20.0, 30.0);
    var measurementFilter = List.of((new FilterCondition("temperature,val", Operator.EQUALS, 20)));
    var startTime = 2L;
    var endTime = 4L;
    var metaData = Map.of("track", 1, "layer", 4, "key", Map.of("subKey", "some data"));
    var limit = 100;
    var skip = 5;

    var spatialDataReference = new SpatialDataReference(1L);
    spatialDataReference.setShepardId(341L);
    spatialDataReference.setCreatedAt(date);
    spatialDataReference.setCreatedBy(user);
    spatialDataReference.setUpdatedAt(update);
    spatialDataReference.setUpdatedBy(updateUser);
    spatialDataReference.setName("SpatialDataReferenceTest");
    spatialDataReference.setDataObject(dataObject);
    spatialDataReference.setSpatialDataContainer(container);
    spatialDataReference.setGeometryFilter(
      "{  \"type\": \"K_NEAREST_NEIGHBOR\",  \"k\": 5,  \"x\": 10,  \"y\": 20,  \"z\": 30}"
    );
    spatialDataReference.setMeasurementsFilter(
      "[{ \"key\": \"temperature,val\", \"operator\": \"EQUALS\", \"value\": 20 }]"
    );
    spatialDataReference.setStartTime(startTime);
    spatialDataReference.setEndTime(endTime);
    spatialDataReference.setMetadata("{ \"track\":1, \"layer\":4, \"key\":{ \"subKey\": \"some data\" } }");
    spatialDataReference.setLimit(limit);
    spatialDataReference.setSkip(skip);

    var converted = new SpatialDataReferenceIO(spatialDataReference);
    assertEquals(spatialDataReference.getShepardId(), converted.getId());
    assertEquals(spatialDataReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("arthur", converted.getCreatedBy());
    assertEquals(spatialDataReference.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("dutch", converted.getUpdatedBy());
    assertEquals(spatialDataReference.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(container.getId(), converted.getSpatialDataContainerId());
    assertEquals(geometryFilter, converted.getGeometryFilter());
    assertEquals(measurementFilter, converted.getMeasurementFilters());
    assertEquals(spatialDataReference.getStartTime(), converted.getStartTime());
    assertEquals(spatialDataReference.getEndTime(), converted.getEndTime());
    assertEquals(metaData, converted.getMetadata());
    assertEquals(spatialDataReference.getLimit(), converted.getLimit());
    assertEquals(spatialDataReference.getSkip(), converted.getSkip());
  }

  @Test
  public void testConversion_ContainerNull() {
    var date = new Date();
    var user = new User("arthur");
    var update = new Date();
    var updateUser = new User("dutch");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(500L);
    var geometryFilter = new KNearestNeighbor(5, 10.0, 20.0, 30.0);
    var measurementFilter = List.of((new FilterCondition("temperature,val", Operator.EQUALS, 20)));
    var startTime = 2L;
    var endTime = 4L;
    var metaData = Map.of("track", 1, "layer", 4, "key", Map.of("subKey", "some data"));
    var limit = 100;
    var skip = 5;

    var spatialDataReference = new SpatialDataReference(1L);
    spatialDataReference.setShepardId(341L);
    spatialDataReference.setCreatedAt(date);
    spatialDataReference.setCreatedBy(user);
    spatialDataReference.setUpdatedAt(update);
    spatialDataReference.setUpdatedBy(updateUser);
    spatialDataReference.setName("SpatialDataReferenceTest");
    spatialDataReference.setDataObject(dataObject);
    spatialDataReference.setGeometryFilter(
      "{  \"type\": \"K_NEAREST_NEIGHBOR\",  \"k\": 5,  \"x\": 10,  \"y\": 20,  \"z\": 30}"
    );
    spatialDataReference.setMeasurementsFilter(
      "[{ \"key\": \"temperature,val\", \"operator\": \"EQUALS\", \"value\": 20 }]"
    );
    spatialDataReference.setStartTime(startTime);
    spatialDataReference.setEndTime(endTime);
    spatialDataReference.setMetadata("{ \"track\":1, \"layer\":4, \"key\":{ \"subKey\": \"some data\" } }");
    spatialDataReference.setLimit(limit);
    spatialDataReference.setSkip(skip);

    var converted = new SpatialDataReferenceIO(spatialDataReference);
    assertEquals(spatialDataReference.getShepardId(), converted.getId());
    assertEquals(spatialDataReference.getCreatedAt(), converted.getCreatedAt());
    assertEquals("arthur", converted.getCreatedBy());
    assertEquals(spatialDataReference.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("dutch", converted.getUpdatedBy());
    assertEquals(spatialDataReference.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(geometryFilter, converted.getGeometryFilter());
    assertEquals(measurementFilter, converted.getMeasurementFilters());
    assertEquals(spatialDataReference.getStartTime(), converted.getStartTime());
    assertEquals(spatialDataReference.getEndTime(), converted.getEndTime());
    assertEquals(metaData, converted.getMetadata());
    assertEquals(spatialDataReference.getLimit(), converted.getLimit());
    assertEquals(spatialDataReference.getSkip(), converted.getSkip());
    assertEquals(-1, converted.getSpatialDataContainerId());
  }
}
