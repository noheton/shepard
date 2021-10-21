package de.dlr.shepard.influxDB;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.ti;
import static org.influxdb.querybuilder.FunctionFactory.time;
import static org.influxdb.querybuilder.time.DurationLiteral.SECOND;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.BuiltQuery.QueryBuilder;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectionQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class InfluxUtil {

	private InfluxUtil() {
		// Static class needs no constructor
	}

	private static final long MULTIPLIER_NANO = 1000000000L;

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
	public static Query buildQuery(long startTimeStamp, long endTimeStamp, String database, Timeseries timeseries,
			AggregateFunction function, Long groupByInterval) {
		String field = "\"" + timeseries.getField() + "\"";
		SelectionQueryImpl selectionQuery;
		if (function == null) {
			selectionQuery = QueryBuilder.select(field);
		} else {
			selectionQuery = QueryBuilder.select().raw(String.format("%s(%s)", function.toString(), field));
		}
		WhereQueryImpl<?> whereQuery = selectionQuery.from(database, "\"" + timeseries.getMeasurement() + "\"")
				.where(gte("time", ti(startTimeStamp, "ns"))).and(lte("time", ti(endTimeStamp, "ns")))
				.and(eq(Constants.DEVICE, timeseries.getDevice())).and(eq(Constants.LOCATION, timeseries.getLocation()))
				.and(eq(Constants.SYMBOLICNAME, timeseries.getSymbolicName()));
		if (groupByInterval == null)
			return whereQuery;
		SelectQueryImpl selectQuery = whereQuery.groupBy(time(groupByInterval, SECOND));
		return selectQuery;
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
		BatchPoints batchPoints = BatchPoints.database(database).build();
		var influxPoints = timeseriesPayload.getPoints();
		var timeseries = timeseriesPayload.getTimeseries();

		for (var influxPoint : influxPoints) {
			Builder pointBuilder = Point.measurement(timeseries.getMeasurement())
					.tag(Constants.LOCATION, timeseries.getLocation()).tag(Constants.DEVICE, timeseries.getDevice())
					.tag(Constants.SYMBOLICNAME, timeseries.getSymbolicName())
					.time(influxPoint.getTimeInNanoseconds(), TimeUnit.NANOSECONDS);
			Object value = influxPoint.getValue();
			if ("string".equalsIgnoreCase(expectedType)) {
				pointBuilder.addField(timeseries.getField(), value.toString());
			} else if (value instanceof String) {
				pointBuilder.addField(timeseries.getField(), (String) value);
			} else if (value instanceof Number) {
				pointBuilder.addField(timeseries.getField(), ((Number) value).doubleValue());
			} else if (value instanceof Boolean) {
				pointBuilder.addField(timeseries.getField(), (Boolean) value);
			} else {
				pointBuilder.addField(timeseries.getField(), value.toString());
			}
			batchPoints.point(pointBuilder.build());
		}

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
