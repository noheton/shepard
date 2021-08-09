package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.influxDB.InfluxPoint;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.DateHelper;

public class TimeseriesContainerServiceTest extends BaseTestCase {

	@Mock
	private TimeseriesContainerDAO dao;

	@Mock
	private TimeseriesService timeseriesService;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private TimeseriesContainerService service;

	@Test
	public void getTimeseriesContainerTest_successful() {
		var container = new TimeseriesContainer(1L);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getTimeseriesContainer(1L);
		assertEquals(container, actual);
	}

	@Test
	public void getTimeseriesContainerTest_isNull() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getTimeseriesContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getTimeseriesContainerTest_isDeleted() {
		var container = new TimeseriesContainer(1L);
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getTimeseriesContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getAllTimeseriesContainerTest_successful() {
		var container1 = new TimeseriesContainer(1L);
		var container2 = new TimeseriesContainer(2L);

		when(dao.findAllTimeseriesContainers(null)).thenReturn(List.of(container1, container2));

		var actual = service.getAllTimeseriesContainers(null);
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

		var actual = service.createTimeseriesContainer(input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void deleteTimeseriesContainerServiceTest() {
		var user = new User("bob");
		var date = new Date(23);
		var old = new TimeseriesContainer(1L);

		var expected = new TimeseriesContainer(1L) {
			{
				setUpdatedAt(date);
				setUpdatedBy(user);
				setDeleted(true);
			}
		};

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(old);
		when(dao.createOrUpdate(expected)).thenReturn(expected);

		var actual = service.deleteTimeseriesContainer(1L, "bob");
		assertTrue(actual);
	}

	@Test
	public void deleteTimeseriesContainerServiceTest_isNull() {
		var user = new User("bob");
		var date = new Date(23);

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(null);

		var actual = service.deleteTimeseriesContainer(1L, "bob");
		assertFalse(actual);
	}

	@Test
	public void createTimeseriesTest() {
		var container = new TimeseriesContainer(1L);
		container.setDatabase("database");
		var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

		when(dao.find(1L)).thenReturn(container);
		when(timeseriesService.createTimeseries("database", payload)).thenReturn("");

		var actual = service.createTimeseries(1L, payload);
		assertEquals(ts, actual);
	}

	@Test
	public void createTimeseriesTest_isNull() {
		var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

		when(dao.find(1L)).thenReturn(null);

		var actual = service.createTimeseries(1L, payload);
		assertNull(actual);
	}

	@Test
	public void createTimeseriesTest_isDeleted() {
		var container = new TimeseriesContainer(1L);
		container.setDatabase("database");
		container.setDeleted(true);
		var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

		when(dao.find(1L)).thenReturn(container);

		var actual = service.createTimeseries(1L, payload);
		assertNull(actual);
	}

	@Test
	public void createTimeseriesTest_influxIssue() {
		var container = new TimeseriesContainer(1L);
		container.setDatabase("database");
		var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

		when(dao.find(1L)).thenReturn(container);
		when(timeseriesService.createTimeseries("database", payload)).thenReturn("error");

		var actual = service.createTimeseries(1L, payload);
		assertNull(actual);
	}

}
