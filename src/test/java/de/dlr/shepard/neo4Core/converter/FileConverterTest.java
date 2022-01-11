package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.ShepardFile;

public class FileConverterTest extends BaseTestCase {

	private FileConverter converter = new FileConverter();

	@Test
	public void toGraphPropertyTest() {
		var date = new Date();
		var file1 = new ShepardFile("oid", date, "name");
		var file2 = new ShepardFile("", date, "");
		var file3 = new ShepardFile();
		var files = List.of(file1, file2, file3);
		var actual = converter.toGraphProperty(files);
		var expected = List.of(makeString(file1), makeString(file2), makeString(file3));

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		var date = new Date();
		var file1 = new ShepardFile("oid", date, "name");
		var file2 = new ShepardFile("", date, "");
		var file3 = new ShepardFile();
		var files = List.of(makeString(file1), makeString(file2), makeString(file3));
		var actual = converter.toEntityAttribute(files);
		var expected = List.of(file1, file2, file3);

		assertEquals(expected, actual);
	}

	private String makeString(ShepardFile file) {
		SimpleDateFormat sdf;
		sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+00:00");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		StringBuilder builder = new StringBuilder();
		builder.append("{\"oid\":");
		if (file.getOid() != null)
			builder.append(String.format("\"%s\"", file.getOid()));
		else
			builder.append("null");
		builder.append(",\"createdAt\":");
		if (file.getCreatedAt() != null)
			builder.append(String.format("\"%s\"", sdf.format(file.getCreatedAt())));
		else
			builder.append("null");
		builder.append(",\"filename\":");
		if (file.getFilename() != null)
			builder.append(String.format("\"%s\"", file.getFilename()));
		else
			builder.append("null");
		builder.append("}");

		return builder.toString();
	}
}
