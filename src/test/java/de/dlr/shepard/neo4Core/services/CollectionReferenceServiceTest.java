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
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.CollectionReferenceDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class CollectionReferenceServiceTest extends BaseTestCase {

	@Mock
	private CollectionReferenceDAO dao;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private CollectionDAO collectionDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private CollectionReferenceService service;

	@Test
	public void getCollectionReferenceTest_successful() {
		var ref = new CollectionReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getCollectionReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getCollectionReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getCollectionReference(1L);
		assertNull(actual);
	}

	@Test
	public void getCollectionReferenceTest_deleted() {
		var ref = new CollectionReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getCollectionReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllCollectionReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new CollectionReference(1L);
		var ref2 = new CollectionReference(2L);
		var ref3 = new CollectionReference(3L);
		ref3.setDeleted(true);
		dataObject.setReferences(List.of(ref1, ref2, ref3));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2, ref3));
		var actual = service.getAllCollectionReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createCollectionReferenceTest() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var date = new Date(30L);
		var referenced = new Collection(100L);

		var input = new CollectionReferenceIO() {
			{
				setName("MyName");
				setReferencedCollectionId(100L);
				setRelationship("MyRelationship");
			}
		};
		var toCreate = new CollectionReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setReferencedCollection(referenced);
				setRelationship("MyRelationship");
			}
		};
		var created = new CollectionReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setReferencedCollection(referenced);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(collectionDAO.find(100L)).thenReturn(referenced);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createCollectionReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void createCollectionReferenceTest_ReferencedIsNull() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var input = new CollectionReferenceIO() {
			{
				setName("MyName");
				setReferencedCollectionId(100L);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(collectionDAO.find(100L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createCollectionReference(200L, input, "Bob"));
	}

	@Test
	public void createCollectionReferenceTest_ReferencedIsDeleted() throws InvalidBodyException {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var referenced = new Collection(100L);
		referenced.setDeleted(true);
		var input = new CollectionReferenceIO() {
			{
				setName("MyName");
				setReferencedCollectionId(100L);
				setRelationship("MyRelationship");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(collectionDAO.find(100L)).thenReturn(referenced);

		assertThrows(InvalidBodyException.class, () -> service.createCollectionReference(200L, input, "Bob"));
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new CollectionReference(1L);
		var expected = new CollectionReference(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(userDAO.find("Bob")).thenReturn(user);
		when(dao.find(1L)).thenReturn(ref);
		when(dateHelper.getDate()).thenReturn(date);
		var actual = service.deleteCollectionReference(1L, "Bob");

		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}

	@Test
	public void getPayloadTest() {
		var referenced = new Collection(100L);
		var reference = new CollectionReference(1L);
		reference.setReferencedCollection(referenced);

		when(dao.find(1L)).thenReturn(reference);
		when(collectionDAO.find(100L)).thenReturn(referenced);
		var actual = service.getPayload(1L);

		assertEquals(referenced, actual);
	}
}
