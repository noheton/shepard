package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataContainerIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredDataContainerIO.class).verify();
	}

	@Test
	public void testConversion() {
		var user = new User("bob");
		var date = new Date();
		var update = new Date();
		var updateUser = new User("claus");

		var obj = new StructuredDataContainer(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("name");
		obj.setMongoId("mongoid");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);

		var converted = new StructuredDataContainerIO(obj);
		assertEquals(converted.getCreatedAt(), date);
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(1L, converted.getId());
		assertEquals("name", converted.getName());
		assertEquals("mongoid", converted.getOid());
		assertEquals(update, converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
	}

}
