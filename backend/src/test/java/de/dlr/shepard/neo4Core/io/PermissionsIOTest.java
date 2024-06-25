package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class PermissionsIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(PermissionsIO.class).verify();
	}

	@Test
	public void testConversion() {
		var perm = new Permissions(5L);
		perm.setEntity(new Collection(123L));
		perm.setOwner(new User("bob"));
		perm.setReader(List.of(new User("reader")));
		perm.setWriter(List.of(new User("writer")));
		perm.setManager(List.of(new User("manager")));

		var converted = new PermissionsIO(perm);
		assertEquals(123L, converted.getEntityId());
		assertEquals("bob", converted.getOwner());
		assertEquals("[reader]", Arrays.toString(converted.getReader()));
		assertEquals("[writer]", Arrays.toString(converted.getWriter()));
		assertEquals("[manager]", Arrays.toString(converted.getManager()));
	}

	@Test
	public void testConversion_ownerIsNull() {
		var perm = new Permissions(5L);
		perm.setEntity(new Collection(123L));
		perm.setOwner(null);

		var converted = new PermissionsIO(perm);
		assertEquals(123L, converted.getEntityId());
		assertNull(converted.getOwner());
	}

}
