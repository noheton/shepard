package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ShepardParserExceptionTest {

	@Test
	public void testDefaultConstructor() {
		var obj = new ShepardParserException();
		assertEquals("A parser error occurred", obj.getMessage());
	}

	@Test
	public void testConstructor() {
		var obj = new ShepardParserException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new ShepardParserException();
		assertEquals(400, obj.getStatusCode());
	}

}
