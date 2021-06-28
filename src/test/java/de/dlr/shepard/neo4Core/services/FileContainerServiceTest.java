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
import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.DateHelper;

public class FileContainerServiceTest extends BaseTestCase {

	@Mock
	private FileContainerDAO dao;

	@Mock
	private FileService fileService;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private FileContainerService service;

	@Test
	public void getFileContainerTest_successful() {
		var container = new FileContainer(1L);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getFileContainer(1L);
		assertEquals(container, actual);
	}

	@Test
	public void getFileContainerTest_isNull() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getFileContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getFileContainerTest_isDeleted() {
		var container = new FileContainer(1L);
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);

		var actual = service.getFileContainer(1L);
		assertNull(actual);
	}

	@Test
	public void getAllFileContainerTest_successful() {
		var container = new FileContainer(1L);
		var containerDeleted = new FileContainer(2L);
		containerDeleted.setDeleted(true);

		when(dao.findAll()).thenReturn(List.of(container, containerDeleted));

		var actual = service.getAllFileContainers();
		assertEquals(List.of(container), actual);
	}

	@Test
	public void createFileContainerTest() {
		var user = new User("bob");
		var date = new Date(32);

		var input = new FileContainerIO() {
			{
				setName("Name");
			}
		};

		var toCreate = new FileContainer() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setMongoId("collection");
				setName("Name");
			}
		};

		var created = new FileContainer() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setMongoId("database");
				setName("Name");
				setId(1L);
			}
		};

		when(fileService.createFileContainer()).thenReturn("collection");
		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);

		var actual = service.createFileContainer(input, "bob");
		assertEquals(created, actual);
	}

	@Test
	public void deleteFileContainerServiceTest() {
		var user = new User("bob");
		var date = new Date(23);
		var old = new FileContainer(1L);
		old.setMongoId("XYZ");

		var expected = new FileContainer(1L) {
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
		when(fileService.deleteFileContainer("XYZ")).thenReturn(true);

		var actual = service.deleteFileContainer(1L, "bob");
		assertTrue(actual);
	}

	@Test
	public void deleteFileContainerServiceTest_isNull() {
		var user = new User("bob");
		var date = new Date(23);

		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		when(dao.find(1L)).thenReturn(null);

		var actual = service.deleteFileContainer(1L, "bob");
		assertFalse(actual);
	}

	@Test
	public void createFileTest() {
		var container = new FileContainer(1L);
		container.setMongoId("mongoId");
		var file = new File("oid", "filename");

		when(dao.find(1L)).thenReturn(container);
		when(fileService.createFile("mongoId", "filename", null)).thenReturn(file);
		var actual = service.createFile(1L, "filename", null);

		assertEquals(file, actual);
	}

	@Test
	public void createFileTest_containerIsNull() {
		when(dao.find(1L)).thenReturn(null);
		var actual = service.createFile(1L, "filename", null);

		assertNull(actual);
	}

	@Test
	public void createFileTest_containerIsDeleted() {
		var container = new FileContainer(1L);
		container.setMongoId("mongoId");
		container.setDeleted(true);

		when(dao.find(1L)).thenReturn(container);
		var actual = service.createFile(1L, "filename", null);

		assertNull(actual);
	}
}
