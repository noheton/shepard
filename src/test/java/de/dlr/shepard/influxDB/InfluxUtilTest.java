package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.dlr.shepard.BaseTestCase;

public class InfluxUtilTest extends BaseTestCase {

	private static String baseQuery = "SELECT %s FROM %s WHERE time >= %dns AND time <= %dns "
			+ "AND \"device\" = $device AND \"location\" = $location AND \"symbolic_name\" = $symbolic_name";

	@Test
	public void buildQueryTest() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, SingleValuedUnaryFunction.MEAN, 123L);
		var expected = String.format(baseQuery, "MEAN(\"field\")", "\"meas\"", 1, 2)
				+ String.format(" GROUP BY time(%dns)", 123);
		var params = "{\"location\":\"loc\",\"device\":\"dev\",\"symbolic_name\":\"name\"}";

		assertEquals(expected, query.getCommand());
		assertEquals("db", query.getDatabase());
		assertEquals(params, URLDecoder.decode(query.getParameterJsonWithUrlEncoded(), StandardCharsets.UTF_8));
	}

	@Test
	public void buildQueryTest_noFunction() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, null, 123L);
		var expected = String.format(baseQuery, "\"field\"", "\"meas\"", 1, 2)
				+ String.format(" GROUP BY time(%dns)", 123);
		var params = "{\"location\":\"loc\",\"device\":\"dev\",\"symbolic_name\":\"name\"}";

		assertEquals(expected, query.getCommand());
		assertEquals("db", query.getDatabase());
		assertEquals(params, URLDecoder.decode(query.getParameterJsonWithUrlEncoded(), StandardCharsets.UTF_8));
	}

	@Test
	public void buildQueryTest_noGroupBy() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, SingleValuedUnaryFunction.MEAN, null);
		var expected = String.format(baseQuery, "MEAN(\"field\")", "\"meas\"", 1, 2);
		var params = "{\"location\":\"loc\",\"device\":\"dev\",\"symbolic_name\":\"name\"}";

		assertEquals(expected, query.getCommand());
		assertEquals("db", query.getDatabase());
		assertEquals(params, URLDecoder.decode(query.getParameterJsonWithUrlEncoded(), StandardCharsets.UTF_8));
	}

	@Test
	public void extractPayloadTest() {
		var t1 = new Date(1634711033);
		var t2 = new Date(1634733688);
		var sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		var multiplier_nano = 1000000L;
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				var valueList = new ArrayList<List<Object>>();
				var value1 = new ArrayList<Object>();
				value1.add(sdf.format(t1));
				value1.add(123);
				valueList.add(value1);
				var value2 = new ArrayList<Object>();
				value2.add(sdf.format(t2));
				value2.add(456);
				valueList.add(value2);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var actual = InfluxUtil.extractPayload(queryResult, ts);

		var p1 = new InfluxPoint(t1.getTime() * multiplier_nano, 123);
		var p2 = new InfluxPoint(t2.getTime() * multiplier_nano, 456);
		var expected = new TimeseriesPayload(ts, List.of(p1, p2));

		assertEquals(expected, actual);
	}

	private static Stream<Arguments> createBatchTest() {
		// @formatter:off
		return Stream.of(
				// String
				Arguments.of("test", "string", "test", "string"),
				Arguments.of(123, "string", "123", "string"),
				Arguments.of(true, "string", "true", "string"),
				Arguments.of(Byte.valueOf("64"), "string", "64", "string"),
				Arguments.of("test", "", "test", "string"),
				// Double
				Arguments.of("123", "float", 123d, "double"),
				Arguments.of(123, "float", 123d, "double"),
				Arguments.of(Byte.valueOf("64"), "float", 64d, "double"),
				Arguments.of(123, "", 123d, "double"),
				// Long
				Arguments.of("123", "integer", 123L, "long"),
				Arguments.of(123, "integer", 123L, "long"),
				Arguments.of(Byte.valueOf("64"), "integer", 64L, "long"),
				// Boolean
				Arguments.of("true", "boolean", true, "boolean"),
				Arguments.of(false, "boolean", false, "boolean"),
				Arguments.of(Byte.valueOf("64"), "boolean", false, "boolean"),
				Arguments.of(true, "", true, "boolean")
				);
		// @formatter:on
	}

	@ParameterizedTest
	@MethodSource
	public void createBatchTest(Object value, String expectedType, Object converted, String clazz) {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(1634711033000000L, value)));

		var point = Point.measurement("meas").tag(Map.of("device", "dev", "location", "loc", "symbolic_name", "name"))
				.time(1634711033000000L, TimeUnit.NANOSECONDS);
		switch (clazz) {
		case "string":
			point.addField("field", (String) converted);
			break;
		case "double":
			point.addField("field", (Double) converted);
			break;
		case "long":
			point.addField("field", (Long) converted);
			break;
		case "boolean":
			point.addField("field", (Boolean) converted);
			break;
		}

		var actual = InfluxUtil.createBatch("db", payload, expectedType);
		var expected = BatchPoints.database("db").point(point.build()).build();

		assertEquals(expected, actual);
	}

	@Test
	public void createBatchTest_NFE() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(1634711033000000L, "test"),
				new InfluxPoint(1634711033000000L, "123"), new InfluxPoint(1634711033000000L, "bla")));

		var point = Point.measurement("meas").tag(Map.of("device", "dev", "location", "loc", "symbolic_name", "name"))
				.time(1634711033000000L, TimeUnit.NANOSECONDS).addField("field", 123d).build();

		var actual = InfluxUtil.createBatch("db", payload, "float");
		var expected = BatchPoints.database("db").point(point).build();

		assertEquals(expected, actual);
	}

	@Test
	public void createBatchTest_Null() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(1634711033000000L, null)));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = BatchPoints.database("db").build();

		assertEquals(expected, actual);
	}

	@Test
	public void isQueryResultValidTest() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				var valueList = new ArrayList<List<Object>>();
				var value = new ArrayList<Object>();
				value.add(123);
				value.add(456);
				valueList.add(value);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertTrue(actual);
	}

	@Test
	public void isQueryResultValidTest_isNull() {
		var actual = InfluxUtil.isQueryResultValid(null);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_hasError() {
		var queryResult = new QueryResult() {

			@Override
			public String getError() {
				return "error";
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_resultListNull() {
		var queryResult = new QueryResult() {

			@Override
			public List<Result> getResults() {
				return null;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_resultListEmpty() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_resultHasError() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				result.setError("error");
				results.add(result);
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_seriesListEmpty() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_seriesListNull() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				result.setSeries(null);
				results.add(result);
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void isQueryResultValidTest_valueListNull() {
		var queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				series.setValues(null);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		var actual = InfluxUtil.isQueryResultValid(queryResult);
		assertFalse(actual);
	}

	@Test
	public void sanitizeTestExceptField() {
		Timeseries ts = new Timeseries("meas urement", "dev.ice", "loc/action", "n,ame", "field");
		String sanitize = InfluxUtil.sanitize(ts);
		assertEquals(sanitize,
				"device should not contain whitespaces or dots or slashes or commas: dev.\n"
						+ "measurement should not contain whitespaces or dots or slashes or commas: meas \n"
						+ "location should not contain whitespaces or dots or slashes or commas: loc/\n"
						+ "symbolicName should not contain whitespaces or dots or slashes or commas: n,\n" + "");
	}

	@Test
	public void sanitizeTestOnlyField() {
		Timeseries ts = new Timeseries("measurement", "device", "locaction", "name", "fi eld");
		String sanitize = InfluxUtil.sanitize(ts);
		assertEquals(sanitize, "field should not contain whitespaces or dots or slashes or commas: fi \n" + "");
	}

}
