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
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;

public class BasicReferenceServiceTest extends BaseTestCase {

	@Mock
	private BasicReferenceDAO dao;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@InjectMocks
	private BasicReferenceService service;

	@Test
	public void getBasicReferenceTest_successful() {
		var ref = new BasicReference(1L);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getBasicReference(1L);
		assertEquals(ref, actual);
	}

	@Test
	public void getBasicReferenceTest_notFound() {
		when(dao.find(1L)).thenReturn(null);

		var actual = service.getBasicReference(1L);
		assertNull(actual);
	}

	@Test
	public void getBasicReferenceTest_deleted() {
		var ref = new BasicReference(1L);
		ref.setDeleted(true);

		when(dao.find(1L)).thenReturn(ref);

		var actual = service.getBasicReference(1L);
		assertNull(actual);
	}

	@Test
	public void getAllBasicReferencesTest() {
		var ref1 = new BasicReference(1L);
		var ref2 = new BasicReference(2L);
		var ref3 = new BasicReference(3L);
		ref3.setDeleted(true);

		var params = new QueryParamHelper().withName("test");
		when(dao.findByDataObject(200L, params)).thenReturn(List.of(ref1, ref2));
		var actual = service.getAllBasicReferences(200L, params);

		assertEquals(List.of(ref1, ref2), actual);
	}

	@Test
	public void deleteReferenceTest() {
		var user = new User("bob");
		var date = new Date(30L);
		var ref = new BasicReference(1L);
		var expected = new BasicReference(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(dao.find(1L)).thenReturn(ref);
		when(userDAO.find("bob")).thenReturn(user);
		when(dateHelper.getDate()).thenReturn(date);
		var actual = service.deleteBasicReference(1L, "bob");

		verify(dao).createOrUpdate(expected);
		assertTrue(actual);
	}
}
