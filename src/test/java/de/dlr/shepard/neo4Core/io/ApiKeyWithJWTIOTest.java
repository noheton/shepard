package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import nl.jqno.equalsverifier.EqualsVerifier;

public class ApiKeyWithJWTIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(ApiKeyWithJWTIO.class).verify();
	}

	@Test
	public void testConversion() {
		var key = new ApiKey(UUID.randomUUID());
		key.setJws("MyJWS");

		var converted = new ApiKeyWithJWTIO(key);
		assertEquals(key.getUid(), converted.getUid());
		assertEquals("MyJWS", converted.getJwt());
	}

}
