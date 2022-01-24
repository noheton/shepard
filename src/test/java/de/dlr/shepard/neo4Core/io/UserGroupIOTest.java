package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import nl.jqno.equalsverifier.EqualsVerifier;

public class UserGroupIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(UserGroupIO.class).verify();
	}

	@Test
	public void testConversion() {
		UserGroup group = new UserGroup();
		group.setName("group");
		group.setId(1L);
		User user = new User("AKP");
		ArrayList<User> users = new ArrayList<User>();
		users.add(user);
		group.setUsers(users);
		var converted = new UserGroupIO(group);
		assertEquals(converted.getId(), 1L);
		assertEquals(converted.getName(), "group");
		assertEquals(converted.getUsernames().length, 1);
		assertEquals(converted.getUsernames()[0], "AKP");
	}

}
