package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class InfluxUtilTest extends BaseTestCase {

	@Test
	public void buildQueryTest() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, AggregateFunction.MEAN, 123L);
		var expected = "SELECT MEAN(\"field\") FROM \"meas\" WHERE time >= 1ns AND time <= 2ns "
				+ "AND device = 'dev' AND location = 'loc' AND symbolic_name = 'name' GROUP BY time(123s);";
		assertEquals(expected, query.getCommand());
	}

	@Test
	public void buildQueryTest_noFunction() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, null, 123L);
		var expected = "SELECT \"field\" FROM \"meas\" WHERE time >= 1ns AND time <= 2ns "
				+ "AND device = 'dev' AND location = 'loc' AND symbolic_name = 'name' GROUP BY time(123s);";
		assertEquals(expected, query.getCommand());
	}

	@Test
	public void buildQueryTest_noGroupBy() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var query = InfluxUtil.buildQuery(1L, 2L, "db", ts, AggregateFunction.MEAN, null);
		var expected = "SELECT MEAN(\"field\") FROM \"meas\" WHERE time >= 1ns AND time <= 2ns "
				+ "AND device = 'dev' AND location = 'loc' AND symbolic_name = 'name';";
		assertEquals(expected, query.getCommand());
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

	@Test
	public void createBatchTest_expectedTypeString() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, 123);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "string");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=\"123\" 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_int() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, 123);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=123.0 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_float() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, 123.5);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=123.5 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_boolean() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, true);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=true 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_string() {
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, "bla");
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=\"bla\" 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_byte() {
		byte b = 123;
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, b);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=123.0 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
	}

	@Test
	public void createBatchTest_object() {
		var obj = new Object();
		var ts = new Timeseries("meas", "dev", "loc", "name", "field");
		var point = new InfluxPoint(1634711033000000L, obj);
		var payload = new TimeseriesPayload(ts, List.of(point));

		var actual = InfluxUtil.createBatch("db", payload, "");
		var expected = "meas,device=dev,location=loc,symbolic_name=name field=\"" + obj.toString()
				+ "\" 1634711033000000";

		assertEquals("db", actual.getDatabase());
		assertEquals(1, actual.getPoints().size());
		assertEquals(expected, actual.getPoints().get(0).lineProtocol());
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

}
