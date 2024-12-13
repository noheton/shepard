package de.dlr.shepard.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.timeseries.model.enums.FillOption;
import de.dlr.shepard.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.timeseries.services.TimeseriesService;
import de.dlr.shepard.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class TimeseriesReferenceServiceTest {

  @InjectMock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  TimeseriesService timeseriesService;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  TimeseriesContainerDAO timeseriesContainerDAO;

  @InjectMock
  ReferencedTimeseriesNodeEntityDAO timeseriesDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PermissionsUtil permissionsUtil;

  @Inject
  TimeseriesReferenceService referenceService;

  @InjectMock
  TimeseriesCsvService timeseriesCsvService;

  @Test
  public void getTimeseriesReferenceByShepardIdTest_successful() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    TimeseriesReference actual = referenceService.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_deleted() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    TimeseriesReference actual = referenceService.getReferenceByShepardId(ref.getShepardId());
    assertNull(actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(timeseriesReferenceDAO.findByShepardId(shepardId)).thenReturn(null);
    TimeseriesReference actual = referenceService.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTestIsDeleted() {
    Long shepardId = 15L;
    TimeseriesReference ref = new TimeseriesReference(20L);
    ref.setShepardId(shepardId);
    ref.setDeleted(true);
    when(timeseriesReferenceDAO.findByShepardId(shepardId)).thenReturn(ref);
    TimeseriesReference actual = referenceService.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getAllTimeseriesReferencesTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesReference ref1 = new TimeseriesReference(1L);
    ref1.setShepardId(15L);
    TimeseriesReference ref2 = new TimeseriesReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));
    when(timeseriesReferenceDAO.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<TimeseriesReference> actual = referenceService.getAllReferencesByDataObjectShepardId(
      dataObject.getShepardId()
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    Date date = new Date(30L);
    ReferencedTimeseriesNodeEntity timeseries = new ReferencedTimeseriesNodeEntity(
      "meas",
      "dev",
      "loc",
      "symName",
      "field"
    );

    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setReferencedTimeseriesList(List.of(timeseries));
        setTimeseriesContainerId(container.getId());
      }
    };
    var toCreate = new TimeseriesReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStart(input.getStart());
        setEnd(input.getEnd());
        setReferencedTimeseriesList(List.of(timeseries));
        setTimeseriesContainer(container);
      }
    };
    var created = new TimeseriesReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStart(toCreate.getStart());
        setEnd(toCreate.getEnd());
        setReferencedTimeseriesList(toCreate.getReferencedTimeseriesList());
        setTimeseriesContainer(toCreate.getTimeseriesContainer());
      }
    };
    var createdWithShepardId = new TimeseriesReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStart(created.getStart());
        setEnd(created.getEnd());
        setReferencedTimeseriesList(created.getReferencedTimeseriesList());
        setTimeseriesContainer(created.getTimeseriesContainer());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(300L)).thenReturn(container);
    when(
      timeseriesDAO.find(
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField()
      )
    ).thenReturn(timeseries);
    when(timeseriesReferenceDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(timeseriesReferenceDAO.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    var actual = referenceService.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_timeseriesNotFound() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    Date date = new Date(30L);
    ReferencedTimeseriesNodeEntity timeseries = new ReferencedTimeseriesNodeEntity(
      "meas",
      "dev",
      "loc",
      "symName",
      "field"
    );
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setReferencedTimeseriesList(List.of(timeseries));
        setTimeseriesContainerId(container.getId());
      }
    };
    TimeseriesReference toCreate = new TimeseriesReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStart(input.getStart());
        setEnd(input.getEnd());
        setReferencedTimeseriesList(List.of(timeseries));
        setTimeseriesContainer(container);
      }
    };
    TimeseriesReference created = new TimeseriesReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStart(toCreate.getStart());
        setEnd(toCreate.getEnd());
        setReferencedTimeseriesList(toCreate.getReferencedTimeseriesList());
        setTimeseriesContainer(toCreate.getTimeseriesContainer());
      }
    };
    var createdWithShepardId = new TimeseriesReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStart(created.getStart());
        setEnd(created.getEnd());
        setReferencedTimeseriesList(created.getReferencedTimeseriesList());
        setTimeseriesContainer(created.getTimeseriesContainer());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(
      timeseriesDAO.find(
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField()
      )
    ).thenReturn(null);
    when(timeseriesReferenceDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(timeseriesReferenceDAO.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    TimeseriesReference actual = referenceService.createReferenceByShepardId(
      dataObject.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_invalidTimeseries() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setReferencedTimeseriesList(
          List.of(new ReferencedTimeseriesNodeEntity("me.as", "dev", "loc", "symName", "field"))
        );
        setTimeseriesContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () ->
      referenceService.createReferenceByShepardId(2005L, input, user.getUsername())
    );
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_ContainerIsNull() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    container.setDeleted(true);
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setReferencedTimeseriesList(
          List.of(new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field"))
        );
        setTimeseriesContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () ->
      referenceService.createReferenceByShepardId(2005L, input, user.getUsername())
    );
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_ContainerIsDeleted() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long containerShepardId = 12345L;
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setReferencedTimeseriesList(
          List.of(new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field"))
        );
        setTimeseriesContainerId(containerShepardId);
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(containerShepardId)).thenReturn(null);
    assertThrows(InvalidBodyException.class, () ->
      referenceService.createReferenceByShepardId(2005L, input, user.getUsername())
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    TimeseriesReference expected = new TimeseriesReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    var actual = referenceService.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
    verify(timeseriesReferenceDAO).createOrUpdate(expected);
    assertTrue(actual);
  }

  @Test
  public void getPayloadByShepardIdTest() {
    String username = "Ali Baba";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    TimeseriesWithDataPoints timeseriesWithDataPoints = new TimeseriesWithDataPoints(
      ts.toTimeseries(),
      List.of(new TimeseriesDataPoint(50L, 7))
    );
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      ref.getStart(),
      ref.getEnd(),
      10L,
      FillOption.LINEAR,
      AggregateFunction.MEAN
    );
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      timeseriesService.getManyTimeseriesWithDataPoints(container.getId(), List.of(ts.toTimeseries()), queryParams)
    ).thenReturn(List.of(timeseriesWithDataPoints));
    List<TimeseriesWithDataPoints> actual = referenceService.getReferencedTimeseriesWithDataPointsList(
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("symName"),
      username
    );
    assertEquals(List.of(timeseriesWithDataPoints), actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    String username = "Ali Baba";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDeleted(true);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    List<TimeseriesWithDataPoints> actual = referenceService.getReferencedTimeseriesWithDataPointsList(
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesWithDataPoints(ts.toTimeseries(), Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    String username = "Ali Baba";
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    List<TimeseriesWithDataPoints> actual = referenceService.getReferencedTimeseriesWithDataPointsList(
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesWithDataPoints(ts.toTimeseries(), Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_notAllowed() {
    String username = "Rocco Siffredi";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);

    var actual = referenceService.getReferencedTimeseriesWithDataPointsList(
      15L,
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesWithDataPoints(ts.toTimeseries(), Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void exportByShepardIdTest() throws IOException, InvalidAuthException {
    String username = "Gina Wild";
    ByteArrayInputStream exportedFileStream = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      ref.getStart(),
      ref.getEnd(),
      10L,
      FillOption.LINEAR,
      AggregateFunction.MEAN
    );
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      timeseriesCsvService.exportManyTimeseriesWithDataPointsToCsv(
        container.getId(),
        List.of(ts.toTimeseries()),
        queryParams
      )
    ).thenReturn(exportedFileStream);

    var actual = referenceService.exportReferencedTimeseriesByShepardId(
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("symName"),
      username
    );
    assertEquals(exportedFileStream, actual);
  }

  @Test
  public void exportByShepardIdTest_lessParams() throws IOException, InvalidAuthException {
    String username = "Gina Wild";
    ByteArrayInputStream is = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      referenceService.exportReferencedTimeseriesByShepardId(
        ref.getShepardId(),
        null,
        null,
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        username
      )
    ).thenReturn(is);
    var actual = referenceService.exportReferencedTimeseriesByShepardId(ref.getShepardId(), username);
    assertEquals(is, actual);
  }

  @Test
  public void exportByShepardIdTest_notAllowed() throws IOException, InvalidAuthException {
    String username = "Alektra Blue";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);
    assertThrows(InvalidAuthException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        15L,
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        username
      )
    );
  }

  @Test
  public void exportByShepardIdTest_ContainerIsDeleted() throws IOException {
    String username = "Ali Baba";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDeleted(true);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    assertThrows(InvalidRequestException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        username
      )
    );
  }

  @Test
  public void exportByShepardIdTest_ContainerIsNull() throws IOException {
    String username = "Ali Baba";
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
      }
    };
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId())).thenReturn(ref);
    assertThrows(InvalidRequestException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        username
      )
    );
  }
}
