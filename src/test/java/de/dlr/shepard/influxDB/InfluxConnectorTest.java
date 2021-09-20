package de.dlr.shepard.influxDB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.ti;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.Constants;

public class InfluxConnectorTest extends BaseTestCase {

	@Mock
	private InfluxDB influxDB;

	@InjectMocks
	private InfluxConnector connector;

	private final String database = "my_database";
	private final String measurement = "my_measurement";
	private final String location = "my_location";
	private final String device = "my_device";
	private final String sym_name = "my_sym_name";
	private final String field = "my_field";
	private final long timestamp = System.currentTimeMillis() * 1000000;
	private final long start = 12345L;
	private final long end = 67890L;

	private final Query query = select(field).from(database, measurement).where(gte("time", ti(start, "ns")))
			.and(lte("time", ti(end, "ns"))).and(eq(Constants.DEVICE, device)).and(eq(Constants.LOCATION, location))
			.and(eq(Constants.SYMBOLICNAME, sym_name));

	private final Timeseries expectedTimeseries = new Timeseries(measurement, location, device, sym_name, field);
	private final TimeseriesPayload expectedTimeseriesPayload = new TimeseriesPayload(expectedTimeseries,
			new ArrayList<InfluxPoint>());

	@Test
	public void testGetInstance() {
		var actual = InfluxConnector.getInstance();
		assertNotNull(actual);

		var second = InfluxConnector.getInstance();
		assertEquals(actual, second);
	}

	@Test
	public void testConnectiontestPositive() {
		Pong pong = new Pong();
		pong.setVersion("MyVersion");
		when(influxDB.ping()).thenReturn(pong);
		assertTrue(connector.testConnection());
	}

	@Test
	public void testConnectiontestNegative() {
		Pong pong = new Pong();
		pong.setVersion("unknown");
		when(influxDB.ping()).thenReturn(pong);
		assertFalse(connector.testConnection());
	}

	@Test
	public void testConnectiontestNull() {
		when(influxDB.ping()).thenReturn(null);
		assertFalse(connector.testConnection());
	}

	@Test
	public void testCreateDatabase() {
		connector.createDatabase(database);
		verify(influxDB).query(new Query(String.format("CREATE DATABASE \"%s\"", database)));
	}

	@Test
	public void testDeleteDatabase() {
		connector.deleteDatabase(database);
		verify(influxDB).query(new Query(String.format("DROP DATABASE \"%s\"", database)));
	}

	@Test
	public void testSaveTimeseriesWithExpectedDatatypeInvalid() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				return results;
			}
		};

		when(influxDB.query(new Query(queryString))).thenReturn(queryResult);
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
	}

	@Test
	public void testSaveTimeseriesWithNoExpectedDatatype() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				List<List<Object>> valueList = new ArrayList<List<Object>>();
				List<Object> value = new ArrayList<Object>();
				value.add("AnotherField");
				value.add("string");
				valueList.add(value);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};

		when(influxDB.query(new Query(queryString))).thenReturn(queryResult);
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
	}

	@Test
	public void testSaveTimeseriesWithDatabase() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB, never()).query(new Query("CREATE DATABASE " + database));
	}

	@Test
	public void testSaveTimeseriesWithoutDatabase() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", "anotherDatabase", measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries("anotherDatabase", timeseries);
		assertEquals("The database anotherDatabase does not exist", actual);
		verify(influxDB, never()).query(new Query("CREATE DATABASE anotherDatabase"));
	}

	@Test
	public void testSaveTimeseriesWithDatabaseError() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		QueryResult queryResult = new QueryResult() {
			{
				setError("error");
			}
		};

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(queryResult);

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("The database " + database + " does not exist", actual);
		verify(influxDB, never()).query(new Query("CREATE DATABASE " + database));
	}

	@Test
	public void testSaveTimeseriesWithDatabaseInvalid() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);

		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				return results;
			}
		};

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(queryResult);

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("The database " + database + " does not exist", actual);
		verify(influxDB, never()).query(new Query("CREATE DATABASE " + database));
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveInt() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		int value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				Integer.valueOf(value).doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperInteger() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Integer value = 10;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveDouble() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		double value = Math.PI;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(), value);
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperDouble() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Double value = Math.PI;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(), value);
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveFloat() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		float value = 1.98f;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				Float.valueOf(value).doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperFloat() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Float value = 1.98f;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveLong() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		long value = 1L;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				Long.valueOf(value).doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperLong() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Long value = 1L;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveShort() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		short value = 1;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				Short.valueOf(value).doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperShort() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Short value = 1;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsPrimitiveByte() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		byte value = 1;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				Byte.valueOf(value).doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsWrapperByte() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Byte value = 1;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.doubleValue());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsString() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Object value = "HalloIchBinEinTest";
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.toString());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsBoolean() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Object value = true;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				(boolean) value);
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldIsOther() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Object value = new Object();
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.toString());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesException() {
		String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
		Object value = new Object();
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.toString());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));
		doThrow(new InfluxDBException("My Exception")).when(influxDB).write(points);
		var actual = connector.saveTimeseries(database, timeseries);

		assertEquals("My Exception", actual);
	}

	@Test
	public void testSaveTimeseriesWithFieldKeyString() {
		String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
		Object value = 142L;
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.toString());
		points.point(pointBuilder.build());

		when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey("string"));
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testSaveTimeseriesWithFieldKeyUnknown() {
		String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
		Object value = "Test";
		TimeseriesPayload timeseries = configureTimeseries(value);
		BatchPoints points = BatchPoints.database(database).build();
		Builder pointBuilder = configurePointBuilder(timeseries);
		pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(),
				value.toString());
		points.point(pointBuilder.build());

		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				List<List<Object>> valueList = new ArrayList<List<Object>>();
				List<Object> wrong = new ArrayList<Object>();
				wrong.add("wrongField");
				wrong.add("string");
				valueList.add(wrong);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};

		when(influxDB.query(new Query(queryString))).thenReturn(queryResult);
		when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

		var actual = connector.saveTimeseries(database, timeseries);
		assertEquals("", actual);
		verify(influxDB).write(points);
	}

	@Test
	public void testGetTimeseriesWithValidRequestBody() {
		List<List<Object>> values = new ArrayList<List<Object>>();
		List<Object> value = new ArrayList<Object>();
		value.add(Instant.ofEpochMilli(System.currentTimeMillis()).toString());
		value.add(5);
		values.add(value);

		Series series = new Series();
		series.setValues(values);

		Result result = new Result();
		List<Series> seriesList = new ArrayList<Series>();
		seriesList.add(series);
		result.setSeries(seriesList);

		List<Result> resultList = new ArrayList<Result>();
		resultList.add(result);

		QueryResult queryResult = new QueryResult();
		queryResult.setResults(resultList);

		when(influxDB.query(query)).thenReturn(queryResult);

		List<InfluxPoint> exp1 = connector.getTimeseries(start, end, database, expectedTimeseries).getPoints();
		assertEquals((int) exp1.get(0).getValue(), 5);
	}

	@Test
	public void testGetTimeseriesWithQueryResultHasErrors() {
		QueryResult queryResult = new QueryResult();
		queryResult.setError("Some Error");

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithValuesIsNull() {
		List<List<Object>> values = null;

		Series series = new Series();
		series.setValues(values);

		Result result = new Result();
		List<Series> seriesList = new ArrayList<Series>();
		seriesList.add(series);
		result.setSeries(seriesList);

		List<Result> resultList = new ArrayList<Result>();
		resultList.add(result);

		QueryResult queryResult = new QueryResult();
		queryResult.setResults(resultList);

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithQueryResultsListIsEmpty() {
		QueryResult queryResult = new QueryResult();
		queryResult.setResults(new ArrayList<Result>());

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithSpecificResultHasError() {
		QueryResult queryResult = new QueryResult();
		Result result = new Result();
		result.setError("Some Error");
		ArrayList<Result> resultList = new ArrayList<Result>();
		resultList.add(result);
		queryResult.setResults(resultList);

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithSeriesIsNull() {
		QueryResult queryResult = new QueryResult();
		Result result = new Result();
		result.setSeries(null);
		ArrayList<Result> resultList = new ArrayList<Result>();
		resultList.add(result);
		queryResult.setResults(resultList);

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithSeriesIsEmpty() {
		QueryResult queryResult = new QueryResult();
		Result result = new Result();
		result.setSeries(new ArrayList<Series>());
		ArrayList<Result> resultList = new ArrayList<Result>();
		resultList.add(result);
		queryResult.setResults(resultList);

		when(influxDB.query(query)).thenReturn(queryResult);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	@Test
	public void testGetTimeseriesWithInfluxDBException() {
		doThrow(InfluxDBException.class).when(influxDB).query(query);

		TimeseriesPayload actualTimeseries = connector.getTimeseries(start, end, database, expectedTimeseries);
		assertThat(actualTimeseries).usingRecursiveComparison().ignoringFields("influxPoints")
				.isEqualTo(expectedTimeseriesPayload);
		assertTrue(actualTimeseries.getPoints().isEmpty());
	}

	private TimeseriesPayload configureTimeseries(Object value) {
		InfluxPoint influxPoint = new InfluxPoint(timestamp, value);
		List<InfluxPoint> influxPoints = new ArrayList<InfluxPoint>();
		influxPoints.add(influxPoint);
		var timeseries = new Timeseries(measurement, device, location, sym_name, field);
		var payload = new TimeseriesPayload(timeseries, influxPoints);
		return payload;
	}

	private Builder configurePointBuilder(TimeseriesPayload ts) {
		Builder pointBuilder = Point.measurement(ts.getTimeseries().getMeasurement())
				.tag(Constants.LOCATION, ts.getTimeseries().getLocation())
				.tag(Constants.DEVICE, ts.getTimeseries().getDevice())
				.tag(Constants.SYMBOLICNAME, ts.getTimeseries().getSymbolicName());
		return pointBuilder;
	}

	private QueryResult getShowDatabases(String databaseName) {

		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				List<List<Object>> valueList = new ArrayList<List<Object>>();
				List<Object> value = new ArrayList<Object>();
				value.add(databaseName);
				valueList.add(value);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		return queryResult;
	}

	private QueryResult getFieldKey(String fieldKey) {
		QueryResult queryResult = new QueryResult() {
			List<Result> results;

			@Override
			public List<Result> getResults() {
				results = new ArrayList<QueryResult.Result>();
				Result result = new Result();
				ArrayList<Series> seriesList = new ArrayList<Series>();
				Series series = new Series();
				List<List<Object>> valueList = new ArrayList<List<Object>>();
				List<Object> value = new ArrayList<Object>();
				value.add(field);
				value.add(fieldKey);
				valueList.add(value);
				series.setValues(valueList);
				seriesList.add(series);
				result.setSeries(seriesList);
				results.add(result);
				return results;
			}
		};
		return queryResult;
	}
}
