package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class PermissionsTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(Permissions.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(AbstractEntity.class, new Collection(5L), new Collection(6L)).verify();
	}

	@Test
	public void getUniqueIdTest() {
		var perm = new Permissions(5L);
		assertEquals("5", perm.getUniqueId());
	}

	@Test
	public void simpleConstructorTest() {
		var perm1 = new Permissions();
		perm1.setId(5L);
		var perm2 = new Permissions(5L);
		assertEquals(perm1, perm2);
	}
}
