package de.dlr.shepard.data.timeseries.migration.influxtimeseries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class InfluxTimeseriesContainerServiceTest {

  @InjectMock
  TimeseriesContainerDAO dao;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  InfluxTimeseriesService timeseriesService;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  InfluxTimeseriesContainerService service;

  @Test
  public void getTimeseriesContainerTest_successful() {
    var container = new TimeseriesContainer(1L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertEquals(container, actual);
  }

  @Test
  public void getTimeseriesContainerTest_isNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getTimeseriesContainerTest_isDeleted() {
    var container = new TimeseriesContainer(1L);
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getAllTimeseriesContainerTest_successful() {
    var container1 = new TimeseriesContainer(1L);
    var container2 = new TimeseriesContainer(2L);

    when(dao.findAllTimeseriesContainers(null, "bob")).thenReturn(List.of(container1, container2));

    var actual = service.getAllContainers(null, "bob");
    assertEquals(List.of(container1, container2), actual);
  }

  @Test
  public void createTimeseriesContainerTest() {
    var user = new User("bob");
    var date = new Date(32);

    var input = new TimeseriesContainerIO() {
      {
        setName("Name");
      }
    };

    var toCreate = new TimeseriesContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDatabase("database");
        setName("Name");
      }
    };

    var created = new TimeseriesContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDatabase("database");
        setName("Name");
        setId(1L);
      }
    };

    when(timeseriesService.createDatabase()).thenReturn("database");
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find("bob")).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createContainer(input, "bob");
    assertEquals(created, actual);
    verify(permissionsService).createPermissions(created, user, PermissionType.Private);
  }

  @Test
  public void deleteTimeseriesContainerServiceTest() {
    var user = new User("bob");
    var date = new Date(23);
    var old = new TimeseriesContainer(1L);
    old.setDatabase("database");

    var expected = new TimeseriesContainer(1L) {
      {
        setDatabase("database");
        setUpdatedAt(date);
        setUpdatedBy(user);
        setDeleted(true);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);

    var actual = service.deleteContainer(1L, "bob");
    assertTrue(actual);
    verify(timeseriesService).deleteDatabase("database");
  }

  @Test
  public void deleteTimeseriesContainerServiceTest_isNull() {
    var user = new User("bob");
    var date = new Date(23);

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteContainer(1L, "bob");
    assertFalse(actual);
  }

  @Test
  public void createTimeseriesTest() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var payload = new InfluxTimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(timeseriesService.createTimeseries("database", payload)).thenReturn("");

    var actual = service.createTimeseries(1L, payload);
    assertEquals(ts, actual);
  }

  @Test
  public void createTimeseriesTest_isNull() {
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var payload = new InfluxTimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.createTimeseries(1L, payload);
    assertNull(actual);
  }

  @Test
  public void createTimeseriesTest_isDeleted() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    container.setDeleted(true);
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var payload = new InfluxTimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.createTimeseries(1L, payload);
    assertNull(actual);
  }

  @Test
  public void createTimeseriesTest_influxIssue() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var payload = new InfluxTimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(timeseriesService.createTimeseries("database", payload)).thenReturn("error");

    var actual = service.createTimeseries(1L, payload);
    assertNull(actual);
  }

  @Test
  public void getTimeseriesTest() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var payload = new InfluxTimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));
    var start = 123L;
    var end = 456L;

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(
      timeseriesService.getTimeseriesPayload(
        start,
        end,
        "database",
        ts,
        InfluxSingleValuedUnaryFunction.MEAN,
        10L,
        InfluxFillOption.LINEAR
      )
    ).thenReturn(payload);

    var actual = service.getTimeseriesPayload(
      1L,
      ts,
      start,
      end,
      InfluxSingleValuedUnaryFunction.MEAN,
      10L,
      InfluxFillOption.LINEAR
    );
    assertEquals(payload, actual);
  }

  @Test
  public void getTimeseriesTest_containerNull() {
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var start = 123L;
    var end = 456L;

    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.getTimeseriesPayload(
      1L,
      ts,
      start,
      end,
      InfluxSingleValuedUnaryFunction.MEAN,
      10L,
      InfluxFillOption.LINEAR
    );
    assertNull(actual);
  }

  @Test
  public void getTimeseriesTest_containerDeleted() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    container.setDeleted(true);
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var start = 123L;
    var end = 456L;

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.getTimeseriesPayload(
      1L,
      ts,
      start,
      end,
      InfluxSingleValuedUnaryFunction.MEAN,
      10L,
      InfluxFillOption.LINEAR
    );
    assertNull(actual);
  }

  @Test
  public void getTimeseriesAvailableTest() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var expected = List.of(new InfluxTimeseries("meas", "dev", "loc", "symName", "field"));

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(timeseriesService.getTimeseriesAvailable("database")).thenReturn(expected);

    var actual = service.getTimeseriesAvailable(1L);
    assertEquals(expected, actual);
  }

  @Test
  public void getTimeseriesAvailableTest_containerNull() {
    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.getTimeseriesAvailable(1L);
    assertEquals(0, actual.size());
  }

  @Test
  public void getTimeseriesAvailableTest_containerDeleted() {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    container.setDeleted(true);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.getTimeseriesAvailable(1L);
    assertEquals(0, actual.size());
  }

  @Test
  public void exportTimeseriesTest() throws IOException {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var start = 123L;
    var end = 456L;
    var payload = new ByteArrayInputStream("123".getBytes());

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(
      timeseriesService.exportTimeseriesPayload(
        start,
        end,
        "database",
        List.of(ts),
        InfluxSingleValuedUnaryFunction.MEAN,
        10L,
        InfluxFillOption.LINEAR,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet()
      )
    ).thenReturn(payload);

    var actual = service.exportTimeseriesPayload(
      1L,
      ts,
      start,
      end,
      InfluxSingleValuedUnaryFunction.MEAN,
      10L,
      InfluxFillOption.LINEAR
    );
    assertEquals(payload, actual);
  }

  @Test
  public void exportTimeseriesTest_containerNull() throws IOException {
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var start = 123L;
    var end = 456L;

    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.exportTimeseriesPayload(1L, ts, start, end, InfluxSingleValuedUnaryFunction.MEAN, 10L, null);
    assertNull(actual);
  }

  @Test
  public void exportTimeseriesTest_containerDeleted() throws IOException {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    container.setDeleted(true);
    var ts = new InfluxTimeseries("meas", "dev", "loc", "symName", "field");
    var start = 123L;
    var end = 456L;

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.exportTimeseriesPayload(1L, ts, start, end, InfluxSingleValuedUnaryFunction.MEAN, 10L, null);
    assertNull(actual);
  }

  @Test
  public void importTimeseriesTest() throws IOException, InvalidBodyException {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var payload = new ByteArrayInputStream("123".getBytes());

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(timeseriesService.importTimeseries("database", payload)).thenReturn("");

    var actual = service.importTimeseries(1L, payload);
    assertTrue(actual);
  }

  @Test
  public void importTimeseriesTest_Error() throws IOException, InvalidBodyException {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    var payload = new ByteArrayInputStream("123".getBytes());

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(timeseriesService.importTimeseries("database", payload)).thenReturn("error");

    var actual = service.importTimeseries(1L, payload);
    assertFalse(actual);
  }

  @Test
  public void importTimeseriesTest_containerNull() throws IOException, InvalidBodyException {
    var payload = new ByteArrayInputStream("123".getBytes());

    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.importTimeseries(1L, payload);
    assertFalse(actual);
  }

  @Test
  public void importTimeseriesTest_containerDeleted() throws IOException, InvalidBodyException {
    var container = new TimeseriesContainer(1L);
    container.setDatabase("database");
    container.setDeleted(true);
    var payload = new ByteArrayInputStream("123".getBytes());

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.importTimeseries(1L, payload);
    assertFalse(actual);
  }
}
