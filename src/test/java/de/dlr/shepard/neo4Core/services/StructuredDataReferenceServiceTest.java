package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.mongoDB.StructuredDataService;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;

public class StructuredDataReferenceServiceTest extends BaseTestCase {

	@Mock
	private StructuredDataReferenceDAO dao;

	@Mock
	private StructuredDataService structuredDataService;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private StructuredDataContainerDAO structuredDataContainerDAO;

	@Mock
	private StructuredDataDAO structuredDataDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@Mock
	private PermissionsUtil permissionsUtil;

	@InjectMocks
	private StructuredDataReferenceService service;

	@Test
	public void getStructuredDataReferenceTest_successful() {
		var ref = new StructuredDataReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getStructuredDataReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getStructuredDataReferenceTest_deleted() {
		var ref = new StructuredDataReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllStructuredDataReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new StructuredDataReference(1L);
		var ref2 = new StructuredDataReference(2L);
		dataObject.setReferences(List.of(ref1, ref2));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createStructuredDataReferenceTest() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new StructuredDataContainer(300L);
		container.setMongoId("MongoId");
		var date = new Date(30L);
		var structuredData = new StructuredData("oid", new Date(), "name");
		var input = new StructuredDataReferenceIO() {
			{
				setName("MyName");
				setStructuredDataOids(new String[] { "oid" });
				setStructuredDataContainerId(300L);
			}
		};
		var toCreate = new StructuredDataReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStructuredDatas(List.of(structuredData));
				setStructuredDataContainer(container);
			}
		};
		var created = new StructuredDataReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStructuredDatas(List.of(structuredData));
				setStructuredDataContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(structuredDataContainerDAO.find(300L)).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);
		when(structuredDataDAO.find(300L, "oid")).thenReturn(structuredData);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createStructuredDataReferenceTest_notFound() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new StructuredDataContainer(300L);
		container.setMongoId("MongoId");
		var date = new Date(30L);
		var input = new StructuredDataReferenceIO() {
			{
				setName("MyName");
				setStructuredDataOids(new String[] { "oid" });
				setStructuredDataContainerId(300L);
			}
		};
		var toCreate = new StructuredDataReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStructuredDatas(Collections.emptyList());
				setStructuredDataContainer(container);
			}
		};
		var created = new StructuredDataReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setStructuredDatas(Collections.emptyList());
				setStructuredDataContainer(container);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(structuredDataContainerDAO.find(300L)).thenReturn(container);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);
		when(structuredDataDAO.find(300L, "oid")).thenReturn(null);

		var actual = service.createReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createStructuredDataReferenceTest_ContainerIsNull() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var container = new StructuredDataContainer(300L);
		container.setDeleted(true);
		var input = new StructuredDataReferenceIO() {
			{
				setName("MyName");
				setStructuredDataOids(new String[] { "oid" });
				setStructuredDataContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(structuredDataContainerDAO.find(300L)).thenReturn(container);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void createStructuredDataReferenceTest_ContainerIsDeleted() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new StructuredDataReferenceIO() {
			{
				setName("MyName");
				setStructuredDataOids(new String[] { "oid" });
				setStructuredDataContainerId(300L);
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(structuredDataContainerDAO.find(300L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createReference(200L, input, "Bob"));
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new StructuredDataReference(1L);
		var expected = new StructuredDataReference(1L);
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
	public void getAllPayloadTest() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		var structuredDataA = new StructuredData("abc", new Date(), "name");
		var structuredDataB = new StructuredData("def", new Date(), "name");
		ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));

		var payloadA = new StructuredDataPayload(structuredDataA, "json1");
		var payloadB = new StructuredDataPayload(structuredDataB, "json2");

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(true);
		when(structuredDataService.getPayload("mongoId", "abc")).thenReturn(payloadA);
		when(structuredDataService.getPayload("mongoId", "def")).thenReturn(payloadB);

		var actual = service.getAllPayloads(1L, "bob");
		assertEquals(List.of(payloadA, payloadB), actual);
	}

	@Test
	public void getAllPayloadTest_notAllowed() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		var structuredDataA = new StructuredData("abc", new Date(), "name");
		var structuredDataB = new StructuredData("def", new Date(), "name");
		ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));

		var payloadA = new StructuredDataPayload(structuredDataA, null);
		var payloadB = new StructuredDataPayload(structuredDataB, null);

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(false);

		var actual = service.getAllPayloads(1L, "bob");
		assertEquals(List.of(payloadA, payloadB), actual);
		verify(structuredDataService, never()).getPayload(eq("mongoId"), any(String.class));
	}

	@Test
	public void getAllPayloadTest_unknownOid() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		var structuredDataA = new StructuredData("abc", new Date(), "name");
		var structuredDataB = new StructuredData("def", new Date(), "name");
		ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));

		var payloadA = new StructuredDataPayload(structuredDataA, "json1");

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(true);
		when(structuredDataService.getPayload("mongoId", "abc")).thenReturn(payloadA);
		when(structuredDataService.getPayload("mongoId", "def")).thenReturn(null);

		var actual = service.getAllPayloads(1L, "bob");
		assertEquals(List.of(payloadA, new StructuredDataPayload(structuredDataB, null)), actual);
	}

	@Test
	public void getAllPayloadTest_isNull() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		ref.setStructuredDatas(Collections.emptyList());

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(true);

		var actual = service.getAllPayloads(1L, "bob");
		assertEquals(Collections.emptyList(), actual);
	}

	@Test
	public void getPayloadTest() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		var structuredDataA = new StructuredData("abc", new Date(), "name");
		ref.setStructuredDatas(List.of(structuredDataA));

		var payloadA = new StructuredDataPayload(structuredDataA, "json1");

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(true);
		when(structuredDataService.getPayload("mongoId", "abc")).thenReturn(payloadA);

		var actual = service.getPayload(1L, "abc", "bob");
		assertEquals(payloadA, actual);
	}

	@Test
	public void getPayloadTest_notAllowed() {
		var container = new StructuredDataContainer(20L);
		container.setMongoId("mongoId");
		var ref = new StructuredDataReference(1L);
		ref.setStructuredDataContainer(container);
		var structuredDataA = new StructuredData("abc", new Date(), "name");
		ref.setStructuredDatas(List.of(structuredDataA));

		when(dao.find(1L)).thenReturn(ref);
		when(permissionsUtil.isAllowed(20L, AccessType.Read, "bob")).thenReturn(false);

		assertThrows(InvalidAuthException.class, () -> service.getPayload(1L, "abc", "bob"));
	}
}
