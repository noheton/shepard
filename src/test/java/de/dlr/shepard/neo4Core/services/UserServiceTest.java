package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.security.JWTPrincipal;

public class UserServiceTest extends BaseTestCase {

	@Mock
	private UserDAO dao;

	@InjectMocks
	private UserService service;

	@Test
	public void createUserTest() {
		var user = new User("Bob");
		when(dao.createOrUpdate(user)).thenReturn(user);
		var actual = service.createUser(user);
		assertEquals(user, actual);
	}

	@Test
	public void getUserTest() {
		var user = new User("Bob");
		when(dao.find("Bob")).thenReturn(user);
		var actual = service.getUser("Bob");
		assertEquals(user, actual);
	}

	@Test
	public void updateUserTest_noUpdate() {
		var principal = new JWTPrincipal("aud", "iss", "bob", "John", "Doe", "john.doe@example.com", "key",
				new String[0]);
		var user = new User("bob", "John", "Doe", "john.doe@example.com");

		when(dao.find("bob")).thenReturn(user);

		var actual = service.updateUser(principal);
		verify(dao, never()).createOrUpdate(any());
		assertEquals(user, actual);
	}

	@Test
	public void updateUserTest_noUser() {
		var principal = new JWTPrincipal("aud", "iss", "bob", "John", "Doe", "john.doe@example.com", "key",
				new String[0]);
		var user = new User("bob", "John", "Doe", "john.doe@example.com");

		when(dao.find("bob")).thenReturn(null);
		when(dao.createOrUpdate(user)).thenReturn(user);

		var actual = service.updateUser(principal);
		assertEquals(user, actual);
	}

	@Test
	public void updateUserTest_update() {
		var uid = UUID.randomUUID();
		var principal = new JWTPrincipal("aud", "iss", "bob", "new John", "new Doe", "new@example.com", "key",
				new String[0]);
		var user = new User("bob", "John", "Doe", "john.doe@example.com");
		user.setApiKeys(List.of(new ApiKey(uid)));
		user.setSubscriptions(List.of(new Subscription(3L)));
		var expected = new User("bob", "new John", "new Doe", "new@example.com");
		expected.setApiKeys(List.of(new ApiKey(uid)));
		expected.setSubscriptions(List.of(new Subscription(3L)));

		when(dao.find("bob")).thenReturn(user);
		when(dao.createOrUpdate(expected)).thenReturn(expected);

		var actual = service.updateUser(principal);
		assertEquals(expected, actual);
	}

	@Test
	public void updateUserTest_emptyPrincipal() {
		var uid = UUID.randomUUID();
		var principal = new JWTPrincipal("bob", "key");
		var user = new User("bob", "John", "Doe", "john.doe@example.com");
		user.setApiKeys(List.of(new ApiKey(uid)));
		user.setSubscriptions(List.of(new Subscription(3L)));

		when(dao.find("bob")).thenReturn(user);

		var actual = service.updateUser(principal);
		assertEquals(user, actual);
	}
}
