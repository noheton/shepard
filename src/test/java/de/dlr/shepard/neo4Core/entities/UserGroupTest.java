package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class UserGroupTest extends BaseTestCase {

	@Test
	public void simpleConstructorTest() {
		var userGroup1 = new UserGroup();
		userGroup1.setId(1L);
		var userGroup2 = new UserGroup();
		userGroup2.setId(1L);
		assertEquals(userGroup1, userGroup2);
	}

	@Test
	public void equalsContract() {
		ArrayList<User> users1 = new ArrayList<User>();
		User user1 = new User("user1");
		user1.setApiKeys(null);
		user1.setSubscriptions(null);
		users1.add(user1);
		ArrayList<User> users2 = new ArrayList<User>();
		User user2 = new User("user2");
		user2.setApiKeys(null);
		user2.setSubscriptions(null);
		users2.add(new User("user2"));
		User user3 = new User("user3");
		User user4 = new User("user4");
		EqualsVerifier.simple().forClass(UserGroup.class).withPrefabValues(String.class, "group1", "group2")
				.withPrefabValues(Long.class, 1L, 2L).withPrefabValues(User.class, user3, user4).verify();
	}

	@Test
	public void equalsTest0() {
		var userGroup1 = new UserGroup();
		assertTrue(userGroup1.equals(userGroup1));
	}

	@Test
	public void equalsTest1() {
		var userGroup1 = new UserGroup();
		String test = "test";
		assertFalse(userGroup1.equals(test));
	}

	@Test
	public void equalsTest2() {
		var userGroup1 = new UserGroup();
		userGroup1.setName("group");
		userGroup1.setId(1L);
		User user1 = new User("user");
		ArrayList<User> users1 = new ArrayList<User>();
		users1.add(user1);
		userGroup1.setUsers(users1);
		var userGroup2 = new UserGroup();
		userGroup2.setName("group");
		userGroup2.setId(1L);
		User user2 = new User("user");
		ArrayList<User> users2 = new ArrayList<User>();
		users2.add(user2);
		userGroup2.setUsers(users2);
		assertTrue(userGroup1.equals(userGroup2));
	}

}
