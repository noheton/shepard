package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.File;

public class FileConverterTest extends BaseTestCase {

	private FileConverter converter = new FileConverter();

	@Test
	public void toGraphPropertyTest() {
		var files = List.of(new File("oid", "name"), new File("oid2", ""), new File(), new File("oid", null));
		var actual = converter.toGraphProperty(files);
		var expected = List.of("{\"oid\":\"oid\",\"filename\":\"name\"}", "{\"oid\":\"oid2\",\"filename\":\"\"}",
				"{\"oid\":null,\"filename\":null}", "{\"oid\":\"oid\",\"filename\":null}");

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		var files = List.of("{\"oid\":\"oid\",\"filename\":\"name\"}", "{\"oid\":\"oid2\",\"filename\":\"\"}",
				"{\"oid\":null,\"filename\":null}", "{\"oid\":\"oid\",\"filename\":null}");
		var actual = converter.toEntityAttribute(files);
		var expected = List.of(new File("oid", "name"), new File("oid2", ""), new File(), new File("oid", null));

		assertEquals(expected, actual);
	}

}
