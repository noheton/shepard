package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class ProcessingExceptionTest extends BaseTestCase {

	@Test
	public void testConstructor() {
		var obj = new ProcessingException("Message");
		assertEquals("Message", obj.getMessage());
	}

	@Test
	public void testGetStatusCode() {
		var obj = new ProcessingException("");
		assertEquals(500, obj.getStatusCode());
	}
}
