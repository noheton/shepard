package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class UserGroupTest extends BaseTestCase {

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

}
