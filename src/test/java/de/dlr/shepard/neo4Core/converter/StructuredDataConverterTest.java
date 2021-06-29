package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;

public class StructuredDataConverterTest extends BaseTestCase {

	private StructuredDataConverter converter = new StructuredDataConverter();

	@Test
	public void toGraphPropertyTest() {
		var sds = List.of(new StructuredData("oid"), new StructuredData(""), new StructuredData());
		var actual = converter.toGraphProperty(sds);
		var expected = List.of("{\"oid\":\"oid\"}", "{\"oid\":\"\"}", "{\"oid\":null}");

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		var sds = List.of("{\"oid\":\"oid\"}", "{\"oid\":\"\"}", "{\"oid\":null}");
		var actual = converter.toEntityAttribute(sds);
		var expected = List.of(new StructuredData("oid"), new StructuredData(""), new StructuredData());

		assertEquals(expected, actual);
	}

}
