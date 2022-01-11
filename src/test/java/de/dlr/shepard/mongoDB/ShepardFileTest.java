package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class ShepardFileTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(ShepardFile.class).verify();
	}

	@Test
	public void constructorTest() {
		var date = new Date();
		var expected = new ShepardFile();
		expected.setCreatedAt(date);
		expected.setFilename("name");
		var actual = new ShepardFile(date, "name");
		assertEquals(expected, actual);
	}

	@Test
	public void getUniqueIdTest() {
		var file = new ShepardFile("oid", "name");
		var actual = file.getUniqueId();

		assertEquals("oid", actual);
	}

}
