package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredData.class).verify();
	}

	@Test
	public void getUniqueIdTest() {
		var sd = new StructuredData("oid");
		var actual = sd.getUniqueId();

		assertEquals("oid", actual);
	}

}
