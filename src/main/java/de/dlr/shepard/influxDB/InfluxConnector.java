package de.dlr.shepard.influxDB;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.ti;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.BuiltQuery.QueryBuilder;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PropertiesHelper;
import lombok.extern.log4j.Log4j2;

/**
 * Connector for read and write access to the Influx timeseries database. The
 * class represents the lowest level of data access to the Influx database. The
 * Influx database is accessed directly by using query strings.
 */
@Log4j2
public class InfluxConnector implements IConnector {

	private static final long MULTIPLIER_NANO = 1000000000L;

	private InfluxDB influxDB;
	private static InfluxConnector instance = null;

	/**
	 * Private constructor
	 */
	private InfluxConnector() {
	}

	/**
	 * For development reasons, there should always be just one InfluxConnector
	 * instance.
	 * 
	 * @return The one and only InfluxConnector instance.
	 */
	public static InfluxConnector getInstance() {
		if (instance == null) {
			instance = new InfluxConnector();
		}
		return instance;
	}

	/**
	 * Establishes a connection to the Influx server by using the URL saved in the
	 * config.properties file returned by the DatabaseHelper. In addition the
	 * logging is being configured.
	 * 
	 */
	@Override
	public boolean connect() {
		PropertiesHelper helper = new PropertiesHelper();
		String host = helper.getProperty("influx.host");
		String username = helper.getProperty("influx.username");
		String password = helper.getProperty("influx.password");

		influxDB = InfluxDBFactory.connect(String.format("http://%s", host), username, password);
		influxDB.enableBatch(BatchOptions.DEFAULTS.exceptionHandler((failedPoints, throwable) -> {
			log.error("Exception while writing the following points: {}, Exception: {}", failedPoints, throwable);
		}));
		return true;
	}

	@Override
	public boolean disconnect() {
		if (influxDB != null)
			influxDB.close();
		return true;
	}

	/**
	 * Tests whether a connection exists.
	 * 
	 * @return True if connection exists, otherwise false.
	 */
	public boolean testConnection() {
		Pong response = influxDB.ping();
		if (response == null || response.getVersion().equalsIgnoreCase("unknown"))
			return false;
		else
			return true;
	}

	/**
	 * Creates a new database.
	 * 
	 * @param databaseName Name of the new database to be created.
	 */
	public void createDatabase(String databaseName) {
		String query = String.format("CREATE DATABASE \"%s\"", databaseName);
		influxDB.query(new Query(query));
	}

	/**
	 * Deletes a database.
	 * 
	 * @param databaseName Name of the database to be deleted.
	 */
	public void deleteDatabase(String databaseName) {
		influxDB.query(new Query("DROP DATABASE " + databaseName));
	}

	/**
	 * Writes all InfluxPoint objects, saved in the TimeseriesWithInfluxpoints
	 * object, into the influx database. Therefore the method uses the name of the
	 * database, the name of the measurement, the location, the device, the symbolic
	 * name and the name of the field provided by the given
	 * TimeseriesWithInfluxPoints object. The actual write operation is done by
	 * using the unix timestamp in nanoseconds and the value of every InfluxPoint
	 * object in the TimeseriesWithInfluxPoint object. All these variables have to
	 * be defined in the given Timeseries object for a successful write operation.
	 * 
	 * @param database The database to store the payload in
	 * @param payload  Combines the required attributes in a structured way.
	 * @return An error if there was a problem, empty string if all went well
	 */
	public String saveTimeseries(String database, TimeseriesPayload payload) {
		var timeseries = payload.getTimeseries();
		var influxPoints = payload.getPoints();

		if (!databaseExist(database)) {
			return String.format("The database %s does not exist", database);
		}
		String expectedType = getExpectedDatatype(database, timeseries.getMeasurement(), timeseries.getField());

		BatchPoints batchPoints = BatchPoints.database(database).build();
		for (var influxPoint : influxPoints) {
			Builder pointBuilder = Point.measurement(timeseries.getMeasurement())
					.tag(Constants.LOCATION, timeseries.getLocation()).tag(Constants.DEVICE, timeseries.getDevice())
					.tag(Constants.SYMBOLICNAME, timeseries.getSymbolicName())
					.time(influxPoint.getTimeInNanoseconds(), TimeUnit.NANOSECONDS);
			Object value = influxPoint.getValue();
			if (expectedType.equalsIgnoreCase("string")) {
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

		try {
			influxDB.write(batchPoints);
		} catch (InfluxDBException e) {
			log.error("InfluxdbException while writing points: {}", e.getMessage());
			return e.getMessage();
		}
		return "";

	}

	/**
	 * Returns a Timeseries object containing all the influx data points which match
	 * the given conditions consigned to the method. The returned InfluxPoint
	 * objects are in the given measurement of the specific database between the
	 * start and the end timestamp.
	 * 
	 * @param startTimeStamp Start timestamp from which influx values should be
	 *                       returned.
	 * @param endTimeStamp   End timestamp to which influx values should be
	 *                       returned.
	 * @param database       Name of the database.
	 * @param timeseries     Specifies the data to load from database
	 * @return A Timeseries object containing a list of all InfluxPoints in the
	 *         given measurement of the specific database between the two given
	 *         timestamps matching the "device"-tag, the "location"-tag and the
	 *         "symbolic_name"-tag.
	 */

	public TimeseriesPayload getTimeseries(long startTimeStamp, long endTimeStamp, String database,
			Timeseries timeseries) {
		TimeseriesPayload payload = null;
		Query query = QueryBuilder.select("\"" + timeseries.getField() + "\"")
				.from(database, "\"" + timeseries.getMeasurement() + "\"").where(gte("time", ti(startTimeStamp, "ns")))
				.and(lte("time", ti(endTimeStamp, "ns"))).and(eq(Constants.DEVICE, timeseries.getDevice()))
				.and(eq(Constants.LOCATION, timeseries.getLocation()))
				.and(eq(Constants.SYMBOLICNAME, timeseries.getSymbolicName()));
		QueryResult queryResult;
		try {
			queryResult = influxDB.query(query);
		} catch (InfluxDBException e) {
			queryResult = null;
			log.error("Could not parse query: {}", query.getCommand());
		}
		if (isQueryResultValid(queryResult)) {
			var values = queryResult.getResults().get(0).getSeries().get(0).getValues();
			var influxPoints = new ArrayList<InfluxPoint>(values.size());
			for (var value : values) {
				var time = Instant.parse((String) value.get(0));
				var nanoseconds = time.getEpochSecond() * MULTIPLIER_NANO + time.getNano();
				influxPoints.add(new InfluxPoint(nanoseconds, value.get(1)));
			}
			payload = new TimeseriesPayload(timeseries, influxPoints);
		} else {
			payload = new TimeseriesPayload(timeseries, Collections.emptyList());
		}
		return payload;
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
	private boolean isQueryResultValid(QueryResult queryResult) {
		if (queryResult == null) {
			log.warn("Query Result is null");
			return false;
		}
		if (queryResult.getError() != null) {
			log.warn("There was an error while querying the Influxdb: {}", queryResult.getError());
			return false;
		}

		var resultList = queryResult.getResults();
		if (resultList == null || resultList.isEmpty() || resultList.get(0).hasError())
			return false;

		var seriesList = resultList.get(0).getSeries();
		if (seriesList == null || seriesList.isEmpty())
			return false;

		var valueList = seriesList.get(0).getValues();
		if (valueList == null)
			return false;

		return true;
	}

	/**
	 * Checks whether a database exists or not
	 * 
	 * @param database name of database
	 * @return boolean
	 */
	private boolean databaseExist(String database) {
		QueryResult queryResult = influxDB.query(new Query("SHOW DATABASES"));
		if (!isQueryResultValid(queryResult)) {
			log.warn("There was an error while querying the Influxdb for databases");
			return false;
		}

		var values = queryResult.getResults().get(0).getSeries().get(0).getValues();
		for (var databaseName : values) {
			if (databaseName.get(0).toString().trim().equals(database)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns the expected data type of a field or an empty string according to
	 * https://docs.influxdata.com/influxdb/v1.8/write_protocols/line_protocol_reference/#data-types
	 * 
	 * @param database
	 * @param measurement
	 * @param field
	 * @return expected datatype or empty string
	 */
	private String getExpectedDatatype(String database, String measurement, String field) {
		String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
		QueryResult result = influxDB.query(new Query(queryString));
		if (!isQueryResultValid(result)) {
			log.warn("There was an error while querying the Influxdb with query string \"{}\"", queryString);
			return "";
		}

		var values = result.getResults().get(0).getSeries().get(0).getValues();
		for (var value : values) {
			if (value.get(0).equals(field))
				return (String) value.get(1);
		}

		return "";
	}
}
