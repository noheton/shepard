package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class ShepardProcessingExceptionTest extends BaseTestCase {

	@Test
	public void testConstructor() {
		var obj = new ShepardProcessingException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new ShepardProcessingException("");
		assertEquals(500, obj.getResponse().getStatus());
	}
}
