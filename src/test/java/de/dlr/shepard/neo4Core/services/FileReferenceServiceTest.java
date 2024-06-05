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
	public void getFileReferenceByShepardIdTest_successful() {
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		FileReference actual = service.getReferenceByShepardId(ref.getShepardId());
		assertEquals(ref, actual);
	}

	@Test
	public void getFileReferenceByShepardIdTest_notFound() {
		Long shepardId = 15L;
		when(dao.findByShepardId(shepardId)).thenReturn(null);
		FileReference actual = service.getReferenceByShepardId(shepardId);
		assertNull(actual);
	}

	@Test
	public void getFileReferenceByShepardIdTest_deleted() {
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setDeleted(true);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		FileReference actual = service.getReferenceByShepardId(ref.getShepardId());
		assertNull(actual);
	}

	@Test
	public void getAllFileReferencesByShepardIdTest() {
		DataObject dataObject = new DataObject(200L);
		dataObject.setShepardId(2005L);
		FileReference ref1 = new FileReference(1L);
		ref1.setShepardId(15L);
		FileReference ref2 = new FileReference(2L);
		ref2.setShepardId(25L);
		dataObject.setReferences(List.of(ref1, ref2));
		when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
		List<FileReference> actual = service.getAllReferencesByDataObjectShepardId(dataObject.getShepardId());
		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createFileReferenceByShepardIdTest() {
		User user = new User("Bob");
		DataObject dataObject = new DataObject(200L);
		dataObject.setShepardId(2005L);
		FileContainer container = new FileContainer(300L);
		container.setMongoId("mongoId");
		Date date = new Date(30L);
		ShepardFile fileComplete = new ShepardFile("oid", new Date(), "name", "md5");
		FileReferenceIO input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(container.getId());
			}
		};
		FileReference toCreate = new FileReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName(input.getName());
				setFiles(List.of(fileComplete));
				setFileContainer(container);
			}
		};
		FileReference created = new FileReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(toCreate.getDataObject());
				setName(toCreate.getName());
				setFiles(toCreate.getFiles());
				setFileContainer(toCreate.getFileContainer());
			}
		};
		FileReference createdWithShepardId = new FileReference() {
			{
				setId(created.getId());
				setCreatedAt(created.getCreatedAt());
				setCreatedBy(created.getCreatedBy());
				setDataObject(created.getDataObject());
				setName(created.getName());
				setFiles(created.getFiles());
				setFileContainer(created.getFileContainer());
				setShepardId(created.getId());
			}
		};
		when(userDAO.find(user.getUsername())).thenReturn(user);
		when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
		when(fileContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
		when(dateHelper.getDate()).thenReturn(date);
		when(fileDAO.find(container.getId(), "oid")).thenReturn(fileComplete);
		FileReference actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
		assertEquals(createdWithShepardId, actual);
	}

	@Test
	public void createFileReferenceByShepardIdTest_newFileIsNull() {
		User user = new User("Bob");
		DataObject dataObject = new DataObject(200L);
		dataObject.setShepardId(2005L);
		FileContainer container = new FileContainer(300L);
		container.setMongoId("mongoId");
		Date date = new Date(30L);
		FileReferenceIO input = new FileReferenceIO() {
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
		var createdWithShepardId = new FileReference() {
			{
				setId(1L);
				setShepardId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setFiles(Collections.emptyList());
				setFileContainer(container);
			}
		};
		when(userDAO.find(user.getUsername())).thenReturn(user);
		when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
		when(fileContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
		when(dateHelper.getDate()).thenReturn(date);
		when(fileDAO.find(container.getId(), "oid")).thenReturn(null);
		var actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
		assertEquals(createdWithShepardId, actual);
	}

	@Test
	public void createFileReferenceByShepardIdTest_ContainerIsDeleted() {
		User user = new User("Bob");
		DataObject dataObject = new DataObject(200L);
		dataObject.setShepardId(2005L);
		FileContainer container = new FileContainer(300L);
		container.setDeleted(true);
		FileReferenceIO input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(container.getId());
			}
		};
		when(userDAO.find(user.getUsername())).thenReturn(user);
		when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
		when(fileContainerDAO.findLightByNeo4jId(300L)).thenReturn(container);
		assertThrows(InvalidBodyException.class,
				() -> service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername()));
	}

	@Test
	public void createFileReferenceByShepardIdTest_ContainerIsNull() {
		User user = new User("Bob");
		DataObject dataObject = new DataObject(200L);
		dataObject.setShepardId(2005L);
		Long nullFileContainerId = 300L;
		FileReferenceIO input = new FileReferenceIO() {
			{
				setName("MyName");
				setFileOids(new String[] { "oid" });
				setFileContainerId(nullFileContainerId);
			}
		};

		when(userDAO.find(user.getUsername())).thenReturn(user);
		when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
		when(fileContainerDAO.findLightByNeo4jId(nullFileContainerId)).thenReturn(null);
		assertThrows(InvalidBodyException.class,
				() -> service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername()));
	}

	@Test
	public void deleteReferenceByShepardIdTest() {
		User user = new User("Bob");
		Date date = new Date(30L);
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		FileReference expected = new FileReference(ref.getId());
		expected.setShepardId(ref.getShepardId());
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);
		when(userDAO.find(user.getUsername())).thenReturn(user);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(dateHelper.getDate()).thenReturn(date);
		boolean actual = service.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}

	@Test
	public void getPayloadByShepardIdTest() {
		String username = "123";
		String fileOID = "oid";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		NamedInputStream result = new NamedInputStream(fileOID, null, "myInputStream", 123L);

		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(container.getId(), AccessType.Read, username)).thenReturn(true);
		when(fileService.getPayload(container.getMongoId(), fileOID)).thenReturn(result);
		NamedInputStream actual = service.getPayloadByShepardId(ref.getShepardId(), fileOID, username);

		assertEquals(result, actual);
	}

	@Test
	public void getPayloadByShepardIdTest_NotAllowed() {
		String username = "Xrj§84eEi6fY?";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, username)).thenReturn(false);
		assertThrows(InvalidAuthException.class, () -> service.getPayloadByShepardId(15L, "oid", username));
	}

	@Test
	public void getAllPayloadsByShepardIdTest() {
		String username = "123";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));
		var nis1 = new NamedInputStream("oid1", null, "myInputStream", 123L);
		var nis2 = new NamedInputStream("oid1", null, "mySecondStream", 124L);

		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(container.getId(), AccessType.Read, username)).thenReturn(true);
		when(fileService.getPayload(container.getMongoId(), "oid1")).thenReturn(nis1);
		when(fileService.getPayload(container.getMongoId(), "oid2")).thenReturn(nis2);
		var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);

		assertEquals(List.of(nis1, nis2), actual);
	}

	@Test
	public void getAllPayloadsByShepardIdTest_IsNull() {
		String username = "123";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));
		var nis1 = new NamedInputStream("oid1", null, "myInputStream", 123L);

		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(container.getId(), AccessType.Read, username)).thenReturn(true);
		when(fileService.getPayload(container.getMongoId(), "oid1")).thenReturn(nis1);
		when(fileService.getPayload(container.getMongoId(), "oid2")).thenReturn(null);
		var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);

		assertEquals(List.of(nis1), actual);
	}

	@Test
	public void getAllPayloadsByShepardIdTest_ContainerIsDeleted() {
		String username = "123";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		container.setDeleted(true);
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));

		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(container.getId(), AccessType.Read, username)).thenReturn(true);
		var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);

		assertEquals(Collections.EMPTY_LIST, actual);
	}

	@Test
	public void getAllPayloadsByShepardIdTest_ContainerIsNull() {
		String username = "123";
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));

		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);

		assertEquals(Collections.EMPTY_LIST, actual);
	}

	@Test
	public void getAllPayloadsByShepardIdTest_NotAllowed() {
		String username = "Xrj§84eEi6fY?";
		FileContainer container = new FileContainer(20L);
		container.setMongoId("mongoId");
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFileContainer(container);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, username)).thenReturn(false);
		assertThrows(InvalidAuthException.class, () -> service.getAllPayloadsByShepardId(15L, username));
	}

	@Test
	public void getFilesByShepardIdTest() {
		List<ShepardFile> files = List.of(new ShepardFile("a", new Date(), "b", "c"),
				new ShepardFile("d", new Date(), "e", "f"));
		FileReference ref = new FileReference(1L);
		ref.setShepardId(15L);
		ref.setFiles(files);
		when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
		List<ShepardFile> actual = service.getFilesByShepardId(ref.getShepardId());
		assertEquals(files, actual);
	}

}
