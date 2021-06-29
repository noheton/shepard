package de.dlr.shepard.neo4Core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.influxDB.Timeseries;

public class TimeseriesConverterTest extends BaseTestCase {

	private TimeseriesConverter converter = new TimeseriesConverter();

	@Test
	public void toGraphPropertyTest() {
		var files = List.of(new Timeseries("meas", "dev", "loc", "sym", "field"),
				new Timeseries("meas", "", null, "", null), new Timeseries());
		var actual = converter.toGraphProperty(files);
		var expected = List.of(
				"{\"measurement\":\"meas\",\"device\":\"dev\",\"location\":\"loc\",\"symbolicName\":\"sym\",\"field\":\"field\"}",
				"{\"measurement\":\"meas\",\"device\":\"\",\"location\":null,\"symbolicName\":\"\",\"field\":null}",
				"{\"measurement\":null,\"device\":null,\"location\":null,\"symbolicName\":null,\"field\":null}");

		assertEquals(expected, actual);
	}

	@Test
	public void toEntityAttribute() {
		var files = List.of(
				"{\"measurement\":\"meas\",\"device\":\"dev\",\"location\":\"loc\",\"symbolicName\":\"sym\",\"field\":\"field\"}",
				"{\"measurement\":\"meas\",\"device\":\"\",\"location\":null,\"symbolicName\":\"\",\"field\":null}",
				"{\"measurement\":null,\"device\":null,\"location\":null,\"symbolicName\":null,\"field\":null}");
		var actual = converter.toEntityAttribute(files);
		var expected = List.of(new Timeseries("meas", "dev", "loc", "sym", "field"),
				new Timeseries("meas", "", null, "", null), new Timeseries());

		assertEquals(expected, actual);
	}

}
