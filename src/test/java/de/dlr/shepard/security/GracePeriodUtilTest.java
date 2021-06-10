package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class GracePeriodUtilTest extends BaseTestCase {

	@Test
	public void elementIsKnownTest_False() {
		GracePeriodUtil<?> util = new GracePeriodUtil<>(1000);
		assertFalse(util.elementIsKnown("Test"));
	}

	@Test
	public void elementIsKnownTest_True() {
		GracePeriodUtil<?> util = new GracePeriodUtil<>(1000);
		util.elementSeen("Test", null);
		assertTrue(util.elementIsKnown("Test"));
	}

	@Test
	public void elementIsKnownTest_Outdated() throws InterruptedException {
		GracePeriodUtil<?> util = new GracePeriodUtil<>(1);
		util.elementSeen("Test", null);
		Thread.sleep(2);
		assertFalse(util.elementIsKnown("Test"));
	}

	@Test
	public void getObjectTest() {
		GracePeriodUtil<String> util = new GracePeriodUtil<String>(1000);
		util.elementSeen("Test", "String");
		assertEquals("String", util.getValue("Test"));
	}

}
