package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileContainerIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(FileContainerIO.class).verify();
	}

	@Test
	public void testConversion() {
		var user = new User("bob");
		var date = new Date();
		var update = new Date();
		var updateUser = new User("claus");

		var obj = new FileContainer(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("name");
		obj.setMongoId("oid");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);

		var converted = new FileContainerIO(obj);
		assertEquals(date, converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(1L, converted.getId());
		assertEquals("name", converted.getName());
		assertEquals("oid", converted.getOid());
		assertEquals(update, converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
	}

}
