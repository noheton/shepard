package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class CsvConverterTest extends BaseTestCase {

	private CsvConverter service = new CsvConverter();

	private Timeseries ts1 = new Timeseries("meas", "dev", "loc", "sym", "field");
	private Timeseries ts2 = new Timeseries("meas2", "dev2", "loc2", "sym2", "field2");
	private Timeseries ts3 = new Timeseries("meas2", "dev2", "loc2", "sym2", "field3");

	private String csv = "\"DEVICE\",\"FIELD\",\"LOCATION\",\"MEASUREMENT\",\"SYMBOLICNAME\",\"TIMESTAMP\",\"VALUE\"\n"
			+ "\"dev\",\"field\",\"loc\",\"meas\",\"sym\",\"123\",\"string\"\n"
			+ "\"dev\",\"field\",\"loc\",\"meas\",\"sym\",\"234\",\"123\"\n"
			+ "\"dev\",\"field\",\"loc\",\"meas\",\"sym\",\"345\",\"true\"\n"
			+ "\"dev\",\"field\",\"loc\",\"meas\",\"sym\",\"465\",\"another string\"\n"
			+ "\"dev\",\"field\",\"loc\",\"meas\",\"sym\",\"567\",\"\"\n"
			+ "\"dev2\",\"field2\",\"loc2\",\"meas2\",\"sym2\",\"123\",\"string\"\n"
			+ "\"dev2\",\"field2\",\"loc2\",\"meas2\",\"sym2\",\"234\",\"123\"\n"
			+ "\"dev2\",\"field2\",\"loc2\",\"meas2\",\"sym2\",\"345\",\"true\"\n"
			+ "\"dev2\",\"field2\",\"loc2\",\"meas2\",\"sym2\",\"465\",\"another string\"\n"
			+ "\"dev2\",\"field2\",\"loc2\",\"meas2\",\"sym2\",\"567\",\"\"\n"
			+ "\"dev2\",\"field3\",\"loc2\",\"meas2\",\"sym2\",\"123\",\"string\"\n"
			+ "\"dev2\",\"field3\",\"loc2\",\"meas2\",\"sym2\",\"234\",\"123\"\n"
			+ "\"dev2\",\"field3\",\"loc2\",\"meas2\",\"sym2\",\"345\",\"true\"\n"
			+ "\"dev2\",\"field3\",\"loc2\",\"meas2\",\"sym2\",\"465\",\"another string\"\n"
			+ "\"dev2\",\"field3\",\"loc2\",\"meas2\",\"sym2\",\"567\",\"\"\n";

	@Test
	public void toCsvTest() throws IOException {
		var p1 = new InfluxPoint(123L, "string");
		var p2 = new InfluxPoint(234L, 123);
		var p3 = new InfluxPoint(345L, true);
		var p4 = new InfluxPoint(465L, "another string");
		var p5 = new InfluxPoint(567L, null);

		var payload1 = new TimeseriesPayload(ts1, List.of(p1, p2, p3, p4, p5));
		var payload2 = new TimeseriesPayload(ts2, List.of(p1, p2, p3, p4, p5));
		var payload3 = new TimeseriesPayload(ts3, List.of(p1, p2, p3, p4, p5));

		var actual = service.convertToCsv(List.of(payload1, payload2, payload3));
		assertEquals(csv, IOUtils.toString(actual, StandardCharsets.UTF_8));
	}

	@Test
	public void toPayloadTest() throws IOException {
		var p1String = new InfluxPoint(123L, "string");
		var p2String = new InfluxPoint(234L, "123");
		var p3String = new InfluxPoint(345L, "true");
		var p4String = new InfluxPoint(465L, "another string");
		var p5String = new InfluxPoint(567L, "");

		var payload = service.convertToPayload(new ByteArrayInputStream(csv.getBytes()));
		var payload1 = new TimeseriesPayload(ts1, List.of(p1String, p2String, p3String, p4String, p5String));
		var payload2 = new TimeseriesPayload(ts2, List.of(p1String, p2String, p3String, p4String, p5String));
		var payload3 = new TimeseriesPayload(ts3, List.of(p1String, p2String, p3String, p4String, p5String));
		assertTrue(payload.containsAll(List.of(payload1, payload2, payload3)));
	}

}
