package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.User;

public class UserDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private UserDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(User.class, type);
	}

	@Test
	public void findTest() {
		var a = new User("bob");

		when(session.load(User.class, "bob", 1)).thenReturn(a);
		var actual = dao.find("bob");
		assertEquals(a, actual);
	}

	@Test
	public void deleteTest_Successful() {
		var a = new User("bob");

		when(session.load(User.class, "bob")).thenReturn(a);
		doNothing().when(session).delete(a);
		var actual = dao.delete("bob");
		assertTrue(actual);
	}

	@Test
	public void deleteTest_Error() {
		var a = new User("bob");

		when(session.load(User.class, "bob")).thenReturn(null);
		doNothing().when(session).delete(a);
		var actual = dao.delete("bob");
		assertFalse(actual);
	}
}
