package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class InvalidBodyExceptionTest extends BaseTestCase {

	@Test
	public void testDefaultConstructor() {
		var obj = new InvalidBodyException();
		assertEquals("Some of the values provided in the JSON Body are incorrect", obj.getMessage());
	}

	@Test
	public void testConstructor() {
		var obj = new InvalidBodyException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new InvalidBodyException("Message");
		assertEquals(400, obj.getStatusCode());
	}
}
