package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class InvalidPathExceptionTest extends BaseTestCase {

	@Test
	public void testDefaultConstructor() {
		var obj = new InvalidPathException();
		assertEquals("The specified path does not exist", obj.getMessage());
	}

	@Test
	public void testConstructor() {
		var obj = new InvalidPathException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new InvalidPathException();
		assertEquals(404, obj.getResponse().getStatus());
	}
}
