package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.FileReferenceDAO;
import de.dlr.shepard.neo4Core.dao.ShepardFileDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;

public class FileReferenceServiceTest extends BaseTestCase {

	@Mock
	private FileReferenceDAO dao;

	@Mock
	private FileService fileService;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private FileContainerDAO fileContainerDAO;

	@Mock
	private ShepardFileDAO fileDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@Mock
	private PermissionsUtil permissionsUtil;

	@InjectMocks
	private FileReferenceService service;

	@Test
	public void getFileReferenceTest_successful() {
		var ref = new FileReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getFileReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getFileReferenceTest_deleted() {
		var ref = new FileReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllFileReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new FileReference(1L);
		var ref2 = new FileReference(2L);
		dataObject.setReferences(List.of(ref1, ref2));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createFileReferenceTest() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new FileContainer(300L);
		container.setMongoId("mongoId");
		var date = new Date(30L);
		var fileComplete = new ShepardFile("oid", new Date(), "name", "md5");
		var input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(300L);
			}
		};
		var toCreate = new FileReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setFiles(List.of(fileComplete));
				setFileContainer(container);
			}
		};
		var created = new FileReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setFiles(List.of(fileComplete));
				setFileContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(fileContainerDAO.find(300L)).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);
		when(fileDAO.find(300L, "oid")).thenReturn(fileComplete);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createFileReferenceTest_newFileIsNull() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new FileContainer(300L);
		container.setMongoId("mongoId");
		var date = new Date(30L);
		var input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(300L);
			}
		};
		var toCreate = new FileReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setFiles(Collections.emptyList());
				setFileContainer(container);
			}
		};
		var created = new FileReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setFiles(Collections.emptyList());
				setFileContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(fileContainerDAO.find(300L)).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);
		when(fileDAO.find(300L, "oid")).thenReturn(null);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createFileReferenceTest_ContainerIsNull() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new FileContainer(300L);
		container.setDeleted(true);
		var input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(fileContainerDAO.find(300L)).thenReturn(container);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void createFileReferenceTest_ContainerIsDeleted() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(fileContainerDAO.find(300L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new FileReference(1L);
		var expected = new FileReference(1L);
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
		var container = new FileContainer(20L);
		container.setMongoId("mongoId");
		var ref = new FileReference(1L);
		ref.setFileContainer(container);
		var result = new NamedInputStream(null, "myInputStream", 123L);

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(true);
		when(fileService.getPayload("mongoId", "oid")).thenReturn(result);
		var actual = service.getPayload(1L, "oid", "bob");

		assertEquals(result, actual);
	}

	@Test
	public void getPayloadTest_NotAllowed() {
		var container = new FileContainer(20L);
		container.setMongoId("mongoId");
		var ref = new FileReference(1L);
		ref.setFileContainer(container);

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(false);

		assertThrows(InvalidAuthException.class, () -> service.getPayload(1L, "oid", "bob"));
	}

	@Test
	public void getFilesTest() {
		var files = List.of(new ShepardFile("a", new Date(), "b", "c"), new ShepardFile("d", new Date(), "e", "f"));
		var ref = new FileReference(1L);
		ref.setFiles(files);

		when(dao.find(1L)).thenReturn(ref);
		var actual = service.getFiles(1L);

		assertEquals(files, actual);
	}

}
