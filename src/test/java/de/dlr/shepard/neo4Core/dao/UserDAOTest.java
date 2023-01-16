package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

	@Captor
	private ArgumentCaptor<String> queryCaptor;

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

	@Test
	public void searchUsersAllParamsExceptEmailTest() {
		User user = new User("user");
		user.setFirstName("firstName");
		user.setLastName("lastName");
		Map<String, Object> paramsMap = new HashMap<String, Object>();
		paramsMap.put("username", "user");
		paramsMap.put("firstName", "firstName");
		paramsMap.put("lastName", "lastName");
		when(session.query(eq(User.class), queryCaptor.capture(), eq(paramsMap))).thenReturn(List.of(user));
		var actual = dao.searchUsers("user", "firstName", "lastName", null);
		verify(session).query(eq(User.class), any(String.class), eq(paramsMap));
		assertTrue(queryCaptor.getValue().startsWith("MATCH (u:User) WHERE "));
		assertTrue(queryCaptor.getValue().contains(" u.firstName =~ $firstName "));
		assertTrue(queryCaptor.getValue().contains(" u.lastName =~ $lastName "));
		assertTrue(queryCaptor.getValue().contains(" u.username =~ $username "));
		assertTrue(queryCaptor.getValue().endsWith(" RETURN u"));
		assertFalse(queryCaptor.getValue().contains(" u.email =~ $email "));
		assertEquals(List.of(user), actual);
	}

	@Test
	public void searchUsersViaEmailTest() {
		User user = new User("user");
		user.setFirstName("firstName");
		user.setLastName("lastName");
		user.setEmail("email");
		String query = "MATCH (u:User) WHERE u.email =~ $email RETURN u";
		Map<String, Object> paramsMap = Map.of("email", "email");
		when(session.query(User.class, query, paramsMap)).thenReturn(List.of(user));
		var actual = dao.searchUsers(null, null, null, "email");
		verify(session).query(User.class, query, paramsMap);
		assertEquals(List.of(user), actual);
	}

}
