package de.dlr.shepard.context.references.spatialdatareference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.references.spatialdata.services.SpatialDataReferenceService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.endpoints.SpatialDataParamParser;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.CoordinateSequenceComparator;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryComponentFilter;
import org.locationtech.jts.geom.GeometryFilter;

@QuarkusComponentTest
public class SpatialDataReferenceServiceTest {

  @InjectMock
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  SpatialDataContainerDAO spatialDataContainerDAO;

  @InjectMock
  SpatialDataPointService dataPointService;

  @Inject
  SpatialDataReferenceService referenceService;

  @Test
  public void getSpatialDataReferenceByShepardIdTest_success() {
    SpatialDataReference ref = new SpatialDataReference(1L);
    ref.setShepardId(15L);
    when(spatialDataReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    SpatialDataReference actual = referenceService.getReferenceByShepardId(ref.getShepardId(), null);
    assertEquals(ref, actual);
  }

  @Test
  public void getSpatialDataReferenceByShepardIdTest_deleted() {
    SpatialDataReference ref = new SpatialDataReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(spatialDataReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    SpatialDataReference actual = referenceService.getReferenceByShepardId(ref.getShepardId(), null);
    assertNull(actual);
  }

  @Test
  public void getSpatialDataReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(spatialDataReferenceDAO.findByShepardId(shepardId, null)).thenReturn(null);
    SpatialDataReference actual = referenceService.getReferenceByShepardId(shepardId, null);
    assertNull(actual);
  }

  @Test
  public void getAllSpatialDataReferencesTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    SpatialDataReference ref1 = new SpatialDataReference(1L);
    ref1.setShepardId(15L);
    SpatialDataReference ref2 = new SpatialDataReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));
    when(spatialDataReferenceDAO.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<SpatialDataReference> actual = referenceService.getAllReferencesByDataObjectShepardId(
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createSpatialDataReferenceByShepardIdTest() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    SpatialDataContainer container = new SpatialDataContainer(300L);
    Date date = new Date(30L);
    SpatialDataReferenceIO input = new SpatialDataReferenceIO() {
      {
        setName("MyName");
        setSpatialDataContainerId(container.getId());
      }
    };

    SpatialDataReference toCreate = new SpatialDataReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setSpatialDataContainer(container);
      }
    };
    SpatialDataReference created = new SpatialDataReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setSpatialDataContainer(toCreate.getSpatialDataContainer());
      }
    };

    SpatialDataReference createdWithShepardId = new SpatialDataReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setSpatialDataContainer(created.getSpatialDataContainer());
      }
    };

    when(userDAO.find(user.getUsername())).thenReturn(user);

    when(dataObjectDAO.findLightByNeo4jId(dataObject.getShepardId())).thenReturn(dataObject);
    when(spatialDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(spatialDataReferenceDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(spatialDataReferenceDAO.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    SpatialDataReference actual = referenceService.createReferenceByShepardId(
      dataObject.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createSpatialDataReferenceByShepardIdTest_ContainerIsDeleted() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    SpatialDataContainer container = new SpatialDataContainer(300L);
    container.setDeleted(true);
    KNearestNeighbor geometryFilter = new KNearestNeighbor(5, 10.0, 20.0, 30.0);
    SpatialDataReferenceIO input = new SpatialDataReferenceIO() {
      {
        setName("MyName");
        setGeometryFilter(geometryFilter);
        setSpatialDataContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByNeo4jId(dataObject.getShepardId())).thenReturn(dataObject);
    when(spatialDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () ->
      referenceService.createReferenceByShepardId(2005L, input, user.getUsername())
    );
  }

  @Test
  public void createSpatialDataReferenceByShepardIdTest_containerIsNull() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long containerShepardId = 12345L;
    SpatialDataReferenceIO input = new SpatialDataReferenceIO() {
      {
        setName("MyName");
        setSpatialDataContainerId(containerShepardId);
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByNeo4jId(dataObject.getShepardId())).thenReturn(dataObject);
    when(spatialDataContainerDAO.findLightByNeo4jId(containerShepardId)).thenReturn(null);
    assertThrows(InvalidBodyException.class, () ->
      referenceService.createReferenceByShepardId(2005L, input, user.getUsername())
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    SpatialDataReference ref = new SpatialDataReference(1L);
    ref.setShepardId(15L);
    SpatialDataReference expected = new SpatialDataReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedBy(user);
    expected.setUpdatedAt(date);

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(spatialDataReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    var actual = referenceService.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
    verify(spatialDataReferenceDAO).createOrUpdate(expected);
    assertTrue(actual);
  }

  @Test
  public void getPayloadByShepardIdTest() {
    SpatialDataContainer container = new SpatialDataContainer(2L);
    SpatialDataReference ref = new SpatialDataReference() {
      {
        setShepardId(15L);
        setSpatialDataContainer(container);
      }
    };

    List<SpatialDataPointIO> points = new ArrayList<>();
    Map<String, Object> measurements = Map.of("temperature", 5);
    for (int i = 0; i < 10; i++) {
      SpatialDataPointIO point = new SpatialDataPointIO(
        (long) i,
        (double) i,
        (double) i,
        (double) i,
        Collections.emptyMap(),
        measurements
      );
      points.add(point);
    }

    SpatialDataQueryParams params = new SpatialDataQueryParams(
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null,
      null,
      null
    );

    when(spatialDataReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataPointService.getSpatialDataPointIOs(container.getId(), params)).thenReturn(points);
    List<SpatialDataPointIO> actual = referenceService.getReferencePayload(ref.getShepardId());
    assertEquals(points.size(), actual.size());
    assertEquals(points, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    SpatialDataContainer container = new SpatialDataContainer(2L);
    container.setDeleted(true);
    SpatialDataReference ref = new SpatialDataReference() {
      {
        setShepardId(15L);
        setSpatialDataContainer(container);
      }
    };

    List<SpatialDataPointIO> points = new ArrayList<>();
    Map<String, Object> measurements = Map.of("temperature", 5);
    for (int i = 0; i < 10; i++) {
      SpatialDataPointIO point = new SpatialDataPointIO(
        (long) i,
        (double) i,
        (double) i,
        (double) i,
        Collections.emptyMap(),
        measurements
      );
      points.add(point);
    }

    SpatialDataQueryParams params = new SpatialDataQueryParams(
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null,
      null,
      null
    );

    when(spatialDataReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataPointService.getSpatialDataPointIOs(container.getId(), params)).thenReturn(points);
    List<SpatialDataPointIO> actual = referenceService.getReferencePayload(ref.getShepardId());
    assertEquals(points.size(), actual.size());
    assertEquals(points, actual);
  }
}
