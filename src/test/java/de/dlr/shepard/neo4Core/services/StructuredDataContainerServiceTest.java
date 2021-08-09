package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.util.DateHelper;

public class StructuredDataContainerServiceTest extends BaseTestCase {

	@Mock
	private StructuredDataContainerDAO dao;

	@Mock
	private StructuredDataService structuredDataService;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private StructuredDataContainerService service;

	@Test
	public void getStructuredDataContainerTest_successful() {
		var container = new StructuredDataContainer(1L);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getStructuredDataContainer(1L);
		assertEquals(container, actual);
	}

	@Test
	public void getStructuredDataContainerTest_isNull() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getStructuredDataContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getStructuredDataContainerTest_isDeleted() {
		var container = new StructuredDataContainer(1L);
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getStructuredDataContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getAllStructuredDataContainerTest_successful() {
		var container1 = new StructuredDataContainer(1L);
		var container2 = new StructuredDataContainer(2L);

		when(dao.findAllStructuredDataContainers(null)).thenReturn(List.of(container1, container2));

		var actual = service.getAllStructuredDataContainers(null);
		assertEquals(List.of(container1, container2), actual);
	}

	@Test
	public void createStructuredDataContainerTest() {
		var user = new User("bob");
		var date = new Date(32);

		var input = new StructuredDataContainerIO() {
			{
				setName("Name");
			}
		};

		var toCreate = new StructuredDataContainer() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setMongoId("collection");
				setName("Name");
			}
		};

		var created = new StructuredDataContainer() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setMongoId("database");
				setName("Name");
				setId(1L);
			}
		};

		when(structuredDataService.createStructuredDataContainer()).thenReturn("collection");
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);

		var actual = service.createStructuredDataContainer(input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void deleteStructuredDataContainerServiceTest() {
		var user = new User("bob");
		var date = new Date(23);
		var old = new StructuredDataContainer(1L);
		old.setMongoId("XYZ");

		var expected = new StructuredDataContainer(1L) {
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
		when(structuredDataService.deleteStructuredDataContainer("XYZ")).thenReturn(true);

		var actual = service.deleteStructuredDataContainer(1L, "bob");
		assertTrue(actual);
	}

	@Test
	public void deleteStructuredDataContainerServiceTest_isNull() {
		var user = new User("bob");
		var date = new Date(23);

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(null);

		var actual = service.deleteStructuredDataContainer(1L, "bob");
		assertFalse(actual);
	}

	@Test
	public void createStructuredDataTest() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		var payload = new StructuredDataPayload(new StructuredData("oid"), "payload");

		var updated = new StructuredDataContainer(1L);
		updated.setMongoId("mongoId");
		updated.addStructuredData(payload.getStructuredData());

		when(dao.find(1L)).thenReturn(container);
		when(structuredDataService.createStructuredData("mongoId", payload)).thenReturn(new StructuredData("oid"));

		var actual = service.createStructuredData(1L, payload);

		assertEquals(new StructuredData("oid"), actual);
		verify(dao).createOrUpdate(updated);
	}

	@Test
	public void createStructuredDataTest_containerIsNull() {
		var payload = new StructuredDataPayload(new StructuredData("oid"), "payload");

		when(dao.find(1L)).thenReturn(null);

		var actual = service.createStructuredData(1L, payload);

		assertNull(actual);
	}

	@Test
	public void createStructuredDataTest_containerIsDeleted() {
		var container = new StructuredDataContainer(1L);
		container.setDeleted(true);
		var payload = new StructuredDataPayload(new StructuredData("oid"), "payload");

		when(dao.find(1L)).thenReturn(container);

		var actual = service.createStructuredData(1L, payload);

		assertNull(actual);
	}

	@Test
	public void createStructuredDataTest_mongoError() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		var payload = new StructuredDataPayload(new StructuredData("oid"), "payload");

		when(dao.find(1L)).thenReturn(container);
		when(structuredDataService.createStructuredData("mongoId", payload)).thenReturn(null);

		var actual = service.createStructuredData(1L, payload);

		assertNull(actual);
	}

	@Test
	public void getStructuredDataTest() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		var result = new StructuredDataPayload(new StructuredData("oid"), "payload");

		when(dao.find(1L)).thenReturn(container);
		when(structuredDataService.getPayload("mongoId", "oid")).thenReturn(result);

		var actual = service.getStructuredData(1L, "oid");
		assertEquals(result, actual);
	}

	@Test
	public void getStructuredDataTest_containerIsNull() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getStructuredData(1L, "oid");
		assertNull(actual);
	}

	@Test
	public void getStructuredDataTest_containerIsDeleted() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getStructuredData(1L, "oid");
		assertNull(actual);
	}

	@Test
	public void deleteStructuredDataTest() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		container.setStructuredDatas(List.of(new StructuredData("abc"), new StructuredData("123")));

		var updated = new StructuredDataContainer(1L);
		updated.setMongoId("mongoId");
		updated.setStructuredDatas(List.of(new StructuredData("123")));

		when(dao.find(1L)).thenReturn(container);
		when(structuredDataService.deletePayload("mongoId", "abc")).thenReturn(true);

		var actual = service.deleteStructuredData(1L, "abc");
		assertTrue(actual);
		verify(dao).createOrUpdate(updated);
	}

	@Test
	public void deleteStructuredDataTest_deletedFalse() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		container.setStructuredDatas(List.of(new StructuredData("abc"), new StructuredData("123")));

		var updated = new StructuredDataContainer(1L);
		updated.setMongoId("mongoId");
		updated.setStructuredDatas(List.of(new StructuredData("123")));

		when(dao.find(1L)).thenReturn(container);
		when(structuredDataService.deletePayload("mongoId", "abc")).thenReturn(false);

		var actual = service.deleteStructuredData(1L, "abc");
		assertFalse(actual);
		verify(dao, never()).createOrUpdate(updated);
	}

	@Test
	public void deleteStructuredDataTest_containerIsNull() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.deleteStructuredData(1L, "oid");
		assertFalse(actual);
	}

	@Test
	public void deleteStructuredDataTest_containerIsDeleted() {
		var container = new StructuredDataContainer(1L);
		container.setMongoId("mongoId");
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.deleteStructuredData(1L, "oid");
		assertFalse(actual);
	}
}
