package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class DataObjectReferenceServiceTest extends BaseTestCase {

	@Mock
	private DataObjectReferenceDAO dao;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private DataObjectReferenceService service;

	@Test
	public void getDataObjectReferenceTest_successful() {
		var ref = new DataObjectReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getDataObjectReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getDataObjectReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getDataObjectReference(1L);
		assertNull(actual);
	}

	@Test
	public void getDataObjectReferenceTest_deleted() {
		var ref = new DataObjectReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getDataObjectReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllDataObjectReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new DataObjectReference(1L);
		var ref2 = new DataObjectReference(2L);
		var ref3 = new DataObjectReference(3L);
		ref3.setDeleted(true);
		dataObject.setReferences(List.of(ref1, ref2, ref3));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllDataObjectReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createDataObjectReferenceTest() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var date = new Date(30L);
		var referenced = new DataObject(100L);

		var input = new DataObjectReferenceIO() {
			{
				setName("MyName");
				setReferencedDataObjectId(100L);
				setRelationship("MyRelationship");
			}
		};
		var toCreate = new DataObjectReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setReferencedDataObject(referenced);
				setRelationship("MyRelationship");
			}
		};
		var created = new DataObjectReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setReferencedDataObject(referenced);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(dataObjectDAO.find(100L)).thenReturn(referenced);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createDataObjectReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createDataObjectReferenceTest_ReferencedIsNull() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new DataObjectReferenceIO() {
			{
				setName("MyName");
				setReferencedDataObjectId(100L);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(dataObjectDAO.find(100L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createDataObjectReference(200L, input, "Bob"));
	}

	@Test
	public void createDataObjectReferenceTest_ReferencedIsDeleted() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var referenced = new DataObject(100L);
		referenced.setDeleted(true);
		var input = new DataObjectReferenceIO() {
			{
				setName("MyName");
				setReferencedDataObjectId(100L);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(dataObjectDAO.find(100L)).thenReturn(referenced);

		assertThrows(InvalidBodyException.class, () -> service.createDataObjectReference(200L, input, "Bob"));
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new DataObjectReference(1L);
		var expected = new DataObjectReference(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(userDAO.find("Bob")).thenReturn(user);
		when(dao.find(1L)).thenReturn(ref);
		when(dateHelper.getDate()).thenReturn(date);
		var actual = service.deleteDataObjectReference(1L, "Bob");

		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}

	@Test
	public void getPayloadTest() {
		var referenced = new DataObject(100L);
		var reference = new DataObjectReference(1L);
		reference.setReferencedDataObject(referenced);

		when(dao.find(1L)).thenReturn(reference);
		when(dataObjectDAO.find(100L)).thenReturn(referenced);
		var actual = service.getPayload(1L);

		assertEquals(referenced, actual);
	}

	@Test
	public void getPayloadTest_Deleted() {
		var referenced = new DataObject(100L);
		referenced.setDeleted(true);
		var reference = new DataObjectReference(1L);
		reference.setReferencedDataObject(referenced);

		when(dao.find(1L)).thenReturn(reference);
		when(dataObjectDAO.find(100L)).thenReturn(referenced);
		var actual = service.getPayload(1L);

		assertNull(actual);
	}
}
