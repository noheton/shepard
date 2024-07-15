package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class JWTPrincipalTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(JWTPrincipal.class).verify();
	}

	@Test
	public void getNameTest() {
		var obj = new JWTPrincipal("bob", "key");
		assertEquals("bob", obj.getName());
	}
}
