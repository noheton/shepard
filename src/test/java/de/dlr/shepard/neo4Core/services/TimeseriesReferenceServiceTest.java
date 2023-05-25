package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
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
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
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
	private TimeseriesDAO timeseriesDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@Mock
	private PermissionsUtil permissionsUtil;

	@InjectMocks
	private TimeseriesReferenceService service;

	@Test
	public void getTimeseriesReferenceTest_successful() {
		var ref = new TimeseriesReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getTimeseriesReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getTimeseriesReferenceTest_deleted() {
		var ref = new TimeseriesReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllTimeseriesReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new TimeseriesReference(1L);
		var ref2 = new TimeseriesReference(2L);
		dataObject.setReferences(List.of(ref1, ref2));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createTimeseriesReferenceTest() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		var date = new Date(30L);
		var timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(new Timeseries[] { timeseries });
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
				setTimeseries(List.of(timeseries));
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
				setTimeseries(List.of(timeseries));
				setTimeseriesContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);
		when(timeseriesDAO.find("meas", "dev", "loc", "symName", "field")).thenReturn(timeseries);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createTimeseriesReferenceTest_timeseriesNotFound() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		var date = new Date(30L);
		var timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(new Timeseries[] { timeseries });
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
				setTimeseries(List.of(timeseries));
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
				setTimeseries(List.of(timeseries));
				setTimeseriesContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);
		when(timeseriesDAO.find("meas", "dev", "loc", "symName", "field")).thenReturn(null);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createTimeseriesReferenceTest_invalidTimeseries() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(new Timeseries[] { new Timeseries("me.as", "dev", "loc", "symName", "field") });
				setTimeseriesContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void createTimeseriesReferenceTest_ContainerIsNull() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new TimeseriesContainer(300L);
		container.setDeleted(true);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(new Timeseries[] { new Timeseries("meas", "dev", "loc", "symName", "field") });
				setTimeseriesContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(container);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void createTimeseriesReferenceTest_ContainerIsDeleted() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new TimeseriesReferenceIO() {
			{
				setName("MyName");
				setStart(123L);
				setEnd(321L);
				setTimeseries(new Timeseries[] { new Timeseries("meas", "dev", "loc", "symName", "field") });
				setTimeseriesContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(timeseriesContainerDAO.find(300L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
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
		var actual = service.deleteReference(1L, "Bob");

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
		when(permissionsUtil.isAllowed(2L, AccessType.Read, "bob")).thenReturn(true);
		when(timeseriesService.getTimeseriesPayloadList(123, 321, "Database", List.of(ts),
				SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR, Set.of("dev"), Set.of("loc"), Set.of("name")))
				.thenReturn(List.of(payload));

		var actual = service.getTimeseriesPayload(1L, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR,
				Set.of("dev"), Set.of("loc"), Set.of("name"), "bob");
		assertEquals(List.of(payload), actual);
	}

	@Test
	public void getPayloadTest_notAllowed() {
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

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(2L, AccessType.Read, "bob")).thenReturn(false);

		assertThrows(InvalidAuthException.class, () -> service.getTimeseriesPayload(1L, SingleValuedUnaryFunction.MEAN,
				10L, FillOption.LINEAR, Set.of("dev"), Set.of("loc"), Set.of("name"), "bob"));
	}

	@Test
	public void exportTest() throws IOException, InvalidAuthException {
		var is = new ByteArrayInputStream("Hello World".getBytes());
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

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(2L, AccessType.Read, "bob")).thenReturn(true);
		when(timeseriesService.exportTimeseriesPayload(123, 321, "Database", List.of(ts),
				SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR, Set.of("dev"), Set.of("loc"), Set.of("name")))
				.thenReturn(is);

		var actual = service.exportTimeseriesPayload(1L, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR,
				Set.of("dev"), Set.of("loc"), Set.of("name"), "bob");
		assertEquals(is, actual);
	}

	@Test
	public void exportTest_notAllowed() throws IOException, InvalidAuthException {
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

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(2L, AccessType.Read, "bob")).thenReturn(false);

		assertThrows(InvalidAuthException.class,
				() -> service.exportTimeseriesPayload(1L, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR,
						Set.of("dev"), Set.of("loc"), Set.of("name"), "bob"));
	}

}
