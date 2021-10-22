package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class InvalidRequestExceptionTest extends BaseTestCase {

	@Test
	public void testDefaultConstructor() {
		var obj = new InvalidRequestException();
		assertEquals("The specified request cannot be processed", obj.getMessage());
	}

	@Test
	public void testConstructor() {
		var obj = new InvalidRequestException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new InvalidRequestException("Message");
		assertEquals(400, obj.getStatusCode());
	}
}
