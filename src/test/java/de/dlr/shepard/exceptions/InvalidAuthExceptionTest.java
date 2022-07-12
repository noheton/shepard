package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class InvalidAuthExceptionTest extends BaseTestCase {

	@Test
	public void testDefaultConstructor() {
		var obj = new InvalidAuthException();
		assertEquals("Invalid authentication or authorization", obj.getMessage());
	}

	@Test
	public void testConstructor() {
		var obj = new InvalidAuthException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new InvalidAuthException();
		assertEquals(403, obj.getStatusCode());
	}
}
