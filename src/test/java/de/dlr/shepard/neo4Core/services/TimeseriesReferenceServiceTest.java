package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.influxDB.InfluxPoint;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.influxDB.TimeseriesService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class TimeseriesReferenceServiceTest extends BaseTestCase {

	@Mock
	private TimeseriesReferenceDAO dao;

	@Mock
	private TimeseriesService timeseriesService;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private TimeseriesContainerDAO timeseriesContainerDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private TimeseriesReferenceService service;

	@Test
	public void getTimeseriesReferenceTest_successful() {
		var ref = new TimeseriesReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getTimeseriesReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getTimeseriesReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getTimeseriesReference(1L);
		assertNull(actual);
	}

	@Test
	public void getTimeseriesReferenceTest_deleted() {
		var ref = new TimeseriesReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getTimeseriesReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllTimeseriesReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new TimeseriesReference(1L);
		var ref2 = new TimeseriesReference(2L);
		dataObject.setReferences(List.of(ref1, ref2));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllTimeseriesReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createTimeseriesReferenceTest() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		var date = new Date(30L);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
				setTimeseriesContainerId(300L);
			}
		};
		var toCreate = new TimeseriesReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
				setTimeseriesContainer(container);
			}
		};
		var created = new TimeseriesReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
				setTimeseriesContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createTimeseriesReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createTimeseriesReferenceTest_ContainerIsNull() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		container.setDeleted(true);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
				setTimeseriesContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);

		assertThrows(InvalidBodyException.class, () -> service.createTimeseriesReference(200L, input, "Bob"));
	}

	@Test
	public void createTimeseriesReferenceTest_ContainerIsDeleted() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
				setTimeseriesContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createTimeseriesReference(200L, input, "Bob"));
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new TimeseriesReference(1L);
		var expected = new TimeseriesReference(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(userDAO.find("Bob")).thenReturn(user);
		when(dao.find(1L)).thenReturn(ref);
		when(dateHelper.getDate()).thenReturn(date);
		var actual = service.deleteTimeseriesReference(1L, "Bob");

		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}

	@Test
	public void getPayloadTest() {
		var container = new TimeseriesContainer(2L);
		container.setDatabase("Database");
		var ts = new Timeseries("meas", "dev", "loc", "symName", "field");
		var ref = new TimeseriesReference() {
			{
				setId(1L);
				setEnd(321);
				setStart(123);
				setTimeseries(List.of(ts));
				setTimeseriesContainer(container);

			}
		};
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(50L, 7)));

		when(dao.find(1L)).thenReturn(ref);
		when(timeseriesService.getTimeseriesList(123, 321, "Database", List.of(ts), Set.of("dev"), Set.of("loc"),
				Set.of("name"))).thenReturn(List.of(payload));

		var actual = service.getPayload(1L, Set.of("dev"), Set.of("loc"), Set.of("name"));
		assertEquals(List.of(payload), actual);
	}
}
