package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class UUIDHelperTest extends BaseTestCase {

	private UUIDHelper helper = new UUIDHelper();

	@Test
	public void getUUIDTest() {
		var actual = helper.getUUID();
		assertNotNull(actual);
	}
}
