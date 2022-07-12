package de.dlr.shepard.influxDB;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.BoundParameterQuery;
import org.influxdb.dto.BoundParameterQuery.QueryBuilder;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.QueryResult;

import de.dlr.shepard.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class InfluxUtil {

	private InfluxUtil() {
		// Static class needs no constructor
	}

	private static final long MULTIPLIER_NANO = 1000000000L;
	private static final String FIELD_FLOAT = "float";
	private static final String FIELD_INT = "integer";
	private static final String FIELD_STRING = "string";
	private static final String FIELD_BOOL = "boolean";

	/**
	 * Build an influx query with the given parameters.
	 *
	 * @param startTimeStamp  The beginning of the timeseries
	 * @param endTimeStamp    The end of the timeseries
	 * @param database        The database to be queried
	 * @param timeseries      The timeseries whose points are queried
	 * @param function        The aggregate function
	 * @param groupByInterval The time interval measurements get grouped by
	 * @return an influx query
	 */
	public static BoundParameterQuery buildQuery(long startTimeStamp, long endTimeStamp, String database,
			Timeseries timeseries, AggregateFunction function, Long groupByInterval) {
		var selectPart = (function != null)
				? String.format("SELECT %s(\"%s\")", function.toString(), timeseries.getField())
				: String.format("SELECT \"%s\"", timeseries.getField());
		var fromPart = String.format("FROM \"%s\"", timeseries.getMeasurement());
		var wherePart = String.format("WHERE time >= %dns AND time <= %dns "
				+ "AND \"device\" = $device AND \"location\" = $location AND \"symbolic_name\" = $symbolic_name",
				startTimeStamp, endTimeStamp);
		var query = String.join(" ", selectPart, fromPart, wherePart);

		if (groupByInterval != null) {
			query += String.format(" GROUP BY time(%dns)", groupByInterval);
		}
		var parameterizedQuery = QueryBuilder.newQuery(query).forDatabase(database)
				.bind("device", timeseries.getDevice()).bind("location", timeseries.getLocation())
				.bind("symbolic_name", timeseries.getSymbolicName()).create();
		log.debug("Query influxdb {}: {} with params {}", database, parameterizedQuery.getCommand(),
				URLDecoder.decode(parameterizedQuery.getParameterJsonWithUrlEncoded(), StandardCharsets.UTF_8));
		return parameterizedQuery;
	}

	/**
	 * Extract TimeseriesPayload from influx query result.
	 *
	 * @param queryResult Influx query result
	 * @param timeseries  the timeseries to extract
	 * @return TimeseriesPayload
	 */
	public static TimeseriesPayload extractPayload(QueryResult queryResult, Timeseries timeseries) {
		var values = queryResult.getResults().get(0).getSeries().get(0).getValues();
		var influxPoints = new ArrayList<InfluxPoint>(values.size());
		for (var value : values) {
			var time = Instant.parse((String) value.get(0));
			var nanoseconds = time.getEpochSecond() * MULTIPLIER_NANO + time.getNano();
			influxPoints.add(new InfluxPoint(nanoseconds, value.get(1)));
		}
		return new TimeseriesPayload(timeseries, influxPoints);
	}

	/**
	 * Create a batch out of a given list of influx points.
	 *
	 * @param database          The database where the batch is to be stored
	 * @param timeseriesPayload TimeseriesPayload to be stored
	 * @param expectedType      The expected datatype as string
	 * @return influx batch points
	 */
	public static BatchPoints createBatch(String database, TimeseriesPayload timeseriesPayload, String expectedType) {
		String error = "";
		BatchPoints batchPoints = BatchPoints.database(database).build();
		var influxPoints = timeseriesPayload.getPoints();
		var timeseries = timeseriesPayload.getTimeseries();

		for (var influxPoint : influxPoints) {
			Builder pointBuilder = Point.measurement(timeseries.getMeasurement())
					.tag(Constants.LOCATION, timeseries.getLocation()).tag(Constants.DEVICE, timeseries.getDevice())
					.tag(Constants.SYMBOLICNAME, timeseries.getSymbolicName())
					.time(influxPoint.getTimeInNanoseconds(), TimeUnit.NANOSECONDS);
			Object value = influxPoint.getValue();

			if (value != null && expectedType.equals(FIELD_STRING)) {
				// Expected type is string, we use value.toString()
				pointBuilder.addField(timeseries.getField(), value.toString());
			} else if (value instanceof Number numberValue
					&& (expectedType.equals(FIELD_FLOAT) || expectedType.isBlank())) {
				// value is a number and float or nothing is expected
				pointBuilder.addField(timeseries.getField(), numberValue.doubleValue());
			} else if (value instanceof Number numberValue && expectedType.equals(FIELD_INT)) {
				// value is a number and int or nothing is expected
				pointBuilder.addField(timeseries.getField(), numberValue.longValue());
			} else if (value instanceof Boolean booleanValue
					&& (expectedType.equals(FIELD_BOOL) || expectedType.isBlank())) {
				// value is a boolean and boolean or nothing is expected
				pointBuilder.addField(timeseries.getField(), booleanValue);
			} else if (value != null) {
				// value has to be casted
				var stringValue = value.toString();
				try {
					switch (expectedType) {
					case FIELD_FLOAT -> pointBuilder.addField(timeseries.getField(), Double.parseDouble(stringValue));
					case FIELD_INT -> pointBuilder.addField(timeseries.getField(), Long.parseLong(stringValue));
					case FIELD_BOOL -> pointBuilder.addField(timeseries.getField(), Boolean.parseBoolean(stringValue));
					default -> pointBuilder.addField(timeseries.getField(), stringValue);
					}
				} catch (NumberFormatException e) {
					if (error.isBlank())
						// log the first error
						error = String.format("Invalid influx point detected, cannot cast type %s into type %s",
								stringValue, expectedType);
				}
			}
			if (pointBuilder.hasFields())
				batchPoints.point(pointBuilder.build());
		}
		if (!error.isBlank())
			log.error(error);

		return batchPoints;

	}

	/**
	 * Checks whether a QueryResult is valid, meaning that it has no errors and the
	 * results as well as the series-lists are not empty. If this returns true it is
	 * safe to run
	 * {@code queryResult.getResults().get(0).getSeries().get(0).getValues()}
	 *
	 * @param queryResult The QueryResult to be checked.
	 * @return False if QueryResult has errors or results or series are empty, true
	 *         otherwise.
	 */
	public static boolean isQueryResultValid(QueryResult queryResult) {
		if (queryResult == null) {
			log.warn("Query Result is null");
			return false;
		}
		if (queryResult.getError() != null) {
			log.warn("There was an error while querying the Influxdb: {}", queryResult.getError());
			return false;
		}

		var resultList = queryResult.getResults();
		if (resultList == null || resultList.isEmpty())
			return false;

		var result = resultList.get(0);
		if (result.hasError()) {
			log.warn("There was an error while querying the Influxdb: {}", result.getError());
			return false;
		}

		var seriesList = result.getSeries();
		if (seriesList == null || seriesList.isEmpty())
			return false;

		var valueList = seriesList.get(0).getValues();
		if (valueList == null)
			return false;

		return true;
	}
}
