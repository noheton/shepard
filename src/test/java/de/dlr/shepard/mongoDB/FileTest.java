package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class FileTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(File.class).verify();
	}

	@Test
	public void constructorTest() {
		var date = new Date();
		var expected = new File();
		expected.setCreatedAt(date);
		expected.setFilename("name");
		var actual = new File(date, "name");
		assertEquals(expected, actual);
	}

	@Test
	public void getUniqueIdTest() {
		var file = new File("oid", "name");
		var actual = file.getUniqueId();

		assertEquals("oid", actual);
	}

}
