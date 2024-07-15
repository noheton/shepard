package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class DateHelperTest extends BaseTestCase {

	private DateHelper helper = new DateHelper();

	@Test
	public void getDateTest() {
		var actual = helper.getDate();
		assertNotNull(actual);
	}
}
