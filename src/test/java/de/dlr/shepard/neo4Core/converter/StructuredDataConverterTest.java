package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;

public class StructuredDataConverterTest extends BaseTestCase {

	private StructuredDataConverter converter = new StructuredDataConverter();

	@Test
	public void toGraphPropertyTest() {
		var date = new Date();
		var data1 = new StructuredData("oid", date, "name");
		var data2 = new StructuredData("", date, "");
		var data3 = new StructuredData();
		var sds = List.of(data1, data2, data3);
		var actual = converter.toGraphProperty(sds);
		var expected = List.of(makeString(data1), makeString(data2), makeString(data3));

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		var date = new Date();
		var data1 = new StructuredData("oid", date, "name");
		var data2 = new StructuredData("", date, "");
		var data3 = new StructuredData();
		var sds = List.of(makeString(data1), makeString(data2), makeString(data3));
		var actual = converter.toEntityAttribute(sds);
		var expected = List.of(data1, data2, data3);

		assertEquals(expected, actual);
	}

	private String makeString(StructuredData data) {
		SimpleDateFormat sdf;
		sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+00:00");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		StringBuilder builder = new StringBuilder();
		builder.append("{\"oid\":");
		if (data.getOid() != null)
			builder.append(String.format("\"%s\"", data.getOid()));
		else
			builder.append("null");
		builder.append(",\"createdAt\":");
		if (data.getCreatedAt() != null)
			builder.append(String.format("\"%s\"", sdf.format(data.getCreatedAt())));
		else
			builder.append("null");
		builder.append(",\"name\":");
		if (data.getName() != null)
			builder.append(String.format("\"%s\"", data.getName()));
		else
			builder.append("null");
		builder.append("}");

		return builder.toString();
	}

}
