package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.InfluxPoint;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
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
  TimeseriesReferenceDAO dao;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  TimeseriesService timeseriesService;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  TimeseriesContainerDAO timeseriesContainerDAO;

  @InjectMock
  TimeseriesDAO timeseriesDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PermissionsUtil permissionsUtil;

  @Inject
  TimeseriesReferenceService service;

  @Test
  public void getTimeseriesReferenceByShepardIdTest_successful() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    TimeseriesReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_deleted() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    TimeseriesReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertNull(actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    TimeseriesReference actual = service.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTestIsDeleted() {
    Long shepardId = 15L;
    TimeseriesReference ref = new TimeseriesReference(20L);
    ref.setShepardId(shepardId);
    ref.setDeleted(true);
    when(dao.findByShepardId(shepardId)).thenReturn(ref);
    TimeseriesReference actual = service.getReferenceByShepardId(shepardId);
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
    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<TimeseriesReference> actual = service.getAllReferencesByDataObjectShepardId(dataObject.getShepardId());
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
    Timeseries timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(new Timeseries[] { timeseries });
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
        setTimeseries(List.of(timeseries));
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
        setTimeseries(toCreate.getTimeseries());
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
        setTimeseries(created.getTimeseries());
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
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    var actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
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
    Timeseries timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(new Timeseries[] { timeseries });
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
        setTimeseries(List.of(timeseries));
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
        setTimeseries(toCreate.getTimeseries());
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
        setTimeseries(created.getTimeseries());
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
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    TimeseriesReference actual = service.createReferenceByShepardId(
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
        setTimeseries(new Timeseries[] { new Timeseries("me.as", "dev", "loc", "symName", "field") });
        setTimeseriesContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () -> service.createReferenceByShepardId(2005L, input, user.getUsername())
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
        setTimeseries(new Timeseries[] { new Timeseries("meas", "dev", "loc", "symName", "field") });
        setTimeseriesContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () -> service.createReferenceByShepardId(2005L, input, user.getUsername())
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
        setTimeseries(new Timeseries[] { new Timeseries("meas", "dev", "loc", "symName", "field") });
        setTimeseriesContainerId(containerShepardId);
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerDAO.findLightByNeo4jId(containerShepardId)).thenReturn(null);
    assertThrows(InvalidBodyException.class, () -> service.createReferenceByShepardId(2005L, input, user.getUsername())
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
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    var actual = service.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
    verify(dao).createOrUpdate(expected);
    assertTrue(actual);
  }

  @Test
  public void getPayloadByShepardIdTest() {
    String username = "Ali Baba";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    TimeseriesPayload payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(50L, 7)));
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      timeseriesService.getTimeseriesPayloadList(
        ref.getStart(),
        ref.getEnd(),
        container.getDatabase(),
        ref.getTimeseries(),
        SingleValuedUnaryFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name")
      )
    ).thenReturn(List.of(payload));
    List<TimeseriesPayload> actual = service.getTimeseriesPayloadByShepardId(
      ref.getShepardId(),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    String username = "Ali Baba";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    container.setDeleted(true);
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    List<TimeseriesPayload> actual = service.getTimeseriesPayloadByShepardId(
      ref.getShepardId(),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesPayload(ts, Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    String username = "Ali Baba";
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    List<TimeseriesPayload> actual = service.getTimeseriesPayloadByShepardId(
      ref.getShepardId(),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesPayload(ts, Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_notAllowed() {
    String username = "Rocco Siffredi";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);

    var actual = service.getTimeseriesPayloadByShepardId(
      15L,
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    var expected = List.of(new TimeseriesPayload(ts, Collections.emptyList()));
    assertEquals(expected, actual);
  }

  @Test
  public void exportByShepardIdTest() throws IOException, InvalidAuthException {
    String username = "Gina Wild";
    ByteArrayInputStream is = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      timeseriesService.exportTimeseriesPayload(
        ref.getStart(),
        ref.getEnd(),
        container.getDatabase(),
        List.of(ts),
        SingleValuedUnaryFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name")
      )
    ).thenReturn(is);
    var actual = service.exportTimeseriesPayloadByShepardId(
      ref.getShepardId(),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("name"),
      username
    );
    assertEquals(is, actual);
  }

  @Test
  public void exportByShepardIdTest_lessParams() throws IOException, InvalidAuthException {
    String username = "Gina Wild";
    ByteArrayInputStream is = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(
      timeseriesService.exportTimeseriesPayload(
        ref.getStart(),
        ref.getEnd(),
        container.getDatabase(),
        List.of(ts),
        null,
        null,
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet()
      )
    ).thenReturn(is);
    var actual = service.exportTimeseriesPayloadByShepardId(ref.getShepardId(), username);
    assertEquals(is, actual);
  }

  @Test
  public void exportByShepardIdTest_notAllowed() throws IOException, InvalidAuthException {
    String username = "Alektra Blue";
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDatabase("Database");
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);
    assertThrows(InvalidAuthException.class, () ->
      service.exportTimeseriesPayloadByShepardId(
        15L,
        SingleValuedUnaryFunction.MEAN,
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
    container.setDatabase("Database");
    container.setDeleted(true);
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
        setTimeseriesContainer(container);
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsUtil.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    assertThrows(InvalidRequestException.class, () ->
      service.exportTimeseriesPayloadByShepardId(
        ref.getShepardId(),
        SingleValuedUnaryFunction.MEAN,
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
    Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setTimeseries(List.of(ts));
      }
    };
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    assertThrows(InvalidRequestException.class, () ->
      service.exportTimeseriesPayloadByShepardId(
        ref.getShepardId(),
        SingleValuedUnaryFunction.MEAN,
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
