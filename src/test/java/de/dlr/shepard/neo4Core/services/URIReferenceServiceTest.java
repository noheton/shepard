package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.URIReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.DateHelper;

public class URIReferenceServiceTest extends BaseTestCase {

	@Mock
	private URIReferenceDAO dao;

	@Mock
	private DataObjectDAO dataObjectDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private URIReferenceService service;

	@Test
	public void getURIReferenceTest_successful() {
		var ref = new URIReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getURIReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getURIReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getURIReference(1L);
		assertNull(actual);
	}

	@Test
	public void getURIReferenceTest_deleted() {
		var ref = new URIReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getURIReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllURIReferencesTest() {
		var dataObject = new DataObject(200L);
		var ref1 = new URIReference(1L);
		var ref2 = new URIReference(2L);
		dataObject.setReferences(List.of(ref1, ref2));

		when(dao.findByDataObject(200L)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllURIReferences(200L);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void createURIReferenceTest() {
		var user = new User("Bob");
		var dataObject = new DataObject(200L);
		var date = new Date(30L);
		var input = new URIReferenceIO() {
			{
				setName("MyName");
				setUri("http;//example.com");
			}
		};
		var toCreate = new URIReference() {
			{
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setUri("http;//example.com");
			}
		};
		var created = new URIReference() {
			{
				setId(1L);
				setCreatedAt(date);
				setCreatedBy(user);
				setDataObject(dataObject);
				setName("MyName");
				setUri("http;//example.com");
			}
		};

		when(userDAO.find("Bob")).thenReturn(user);
		when(dataObjectDAO.find(200L)).thenReturn(dataObject);
		when(dao.createOrUpdate(toCreate)).thenReturn(created);
		when(dateHelper.getDate()).thenReturn(date);

		var actual = service.createURIReference(200L, input, "Bob");
		assertEquals(created, actual);
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("Bob");
		var date = new Date(30L);
		var ref = new URIReference(1L);
		var expected = new URIReference(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(userDAO.find("Bob")).thenReturn(user);
		when(dao.find(1L)).thenReturn(ref);
		when(dateHelper.getDate()).thenReturn(date);
		var actual = service.deleteURIReference(1L, "Bob");

		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}

}
