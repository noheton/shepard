package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredData.class).verify();
	}

	@Test
	public void constructorTest() {
		var date = new Date();
		var expected = new StructuredData();
		expected.setCreatedAt(date);
		expected.setName("name");
		var actual = new StructuredData("name", date);

		assertEquals(expected, actual);
	}

	@Test
	public void docConstructorTest() {
		var date = new Date();
		var expected = new StructuredData();
		expected.setCreatedAt(date);
		expected.setOid("myOid");
		var document = new Document(Map.of("oid", "myOid", "createdAt", date));
		var actual = new StructuredData(document);

		assertEquals(expected, actual);
	}

	@Test
	public void allArgsConstructorTest() {
		var date = new Date();
		var expected = new StructuredData();
		expected.setOid("oid");
		expected.setCreatedAt(date);
		expected.setName("name");
		var actual = new StructuredData("oid", date, "name");

		assertEquals(expected, actual);
	}

	@Test
	public void getUniqueIdTest() {
		var sd = new StructuredData("oid", new Date(), "name");
		var actual = sd.getUniqueId();

		assertEquals("oid", actual);
	}

}
