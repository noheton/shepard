package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class ApiKeyIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(ApiKeyIO.class).verify();
	}

	@Test
	public void testConversion() {
		var user = new User("bob");
		var date = new Date();
		var key = new ApiKey(UUID.randomUUID());
		key.setBelongsTo(user);
		key.setCreatedAt(date);
		key.setJws("MyJWS");
		key.setName("MyKey");

		var converted = new ApiKeyIO(key);
		assertEquals(user.getUsername(), converted.getBelongsTo());
		assertEquals(date, converted.getCreatedAt());
		assertEquals("MyKey", converted.getName());
		assertEquals(key.getUid(), converted.getUid());
	}

	@Test
	public void testConversionNoUser() {
		var key = new ApiKey(UUID.randomUUID());

		var converted = new ApiKeyIO(key);
		assertNull(converted.getBelongsTo());
	}

}
