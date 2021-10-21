package de.dlr.shepard.influxDB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeseriesService {

	private InfluxConnector influxConnector = InfluxConnector.getInstance();
	private CsvConverter csvConverter = new CsvConverter();

	/**
	 * Creates timeseries and writes them to influxDB
	 *
	 * @param database The database to be queried
	 * @param payload  the Timeseries with InfluxPoints to be created
	 * @return An error if there was a problem, empty string if all went well
	 */
	public String createTimeseries(String database, TimeseriesPayload payload) {
		return influxConnector.saveTimeseries(database, payload);
	}

	/**
	 * Queries the database for timeseries including aggregate functions.
	 *
	 * @param startTimeStamp  The beginning of the timeseries
	 * @param endTimeStamp    The end of the timeseries
	 * @param database        The database to be queried
	 * @param timeseries      The timeseries whose points are queried
	 * @param function        The aggregate function
	 * @param groupByInterval The time interval measurements get grouped by
	 * @return timeseries with influx points
	 */
	public TimeseriesPayload getTimeseries(long startTimeStamp, long endTimeStamp, String database,
			Timeseries timeseries, AggregateFunction function, Long groupByInterval) {
		TimeseriesPayload payload = influxConnector.getTimeseries(startTimeStamp, endTimeStamp, database, timeseries,
				function, groupByInterval);
		return payload;
	}

	/**
	 * Queries the database for many timeseries in parallel. Returns a list of
	 * timeseries. If the filter sets are empty, no filtering takes place.
	 *
	 * @param startTimeStamp        The beginning of the timeseries
	 * @param endTimeStamp          The end of the timeseries
	 * @param database              The database to be queried
	 * @param timeseriesList        The list of timeseries whose points are queried
	 * @param function              The aggregate function
	 * @param groupByInterval       The time interval measurements get grouped by
	 * @param devicesFilterSet      A set of allowed devices or an empty set
	 * @param locationsFilterSet    A set of allowed locations or an empty set
	 * @param symbolicNameFilterSet A set of allowed symbolic names or an empty set
	 * @return a list of timeseries with influx points
	 */
	public List<TimeseriesPayload> getTimeseriesList(long startTimeStamp, long endTimeStamp, String database,
			List<Timeseries> timeseriesList, AggregateFunction function, Long groupByInterval,
			Set<String> devicesFilterSet, Set<String> locationsFilterSet, Set<String> symbolicNameFilterSet) {
		var timeseriesQueue = new ConcurrentLinkedQueue<TimeseriesPayload>();
		timeseriesList.parallelStream().forEach(timeseries -> {
			TimeseriesPayload payload = null;
			if (matchFilter(timeseries, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet)) {
				payload = getTimeseries(startTimeStamp, endTimeStamp, database, timeseries, function, groupByInterval);
			}
			if (payload != null) {
				timeseriesQueue.add(payload);
			}

		});
		return new ArrayList<TimeseriesPayload>(timeseriesQueue);
	}

	/**
	 * Creates a new database called by a random string
	 *
	 * @return String the new database
	 */
	public String createDatabase() {
		String name = UUID.randomUUID().toString();
		influxConnector.createDatabase(name);
		return name;
	}

	public void deleteDatabase(String database) {
		influxConnector.deleteDatabase(database);
	}

	private boolean matchFilter(Timeseries timeseries, Set<String> device, Set<String> location, Set<String> symName) {
		var deviceMatches = true;
		var locatioMatches = true;
		var symbolicNameMatches = true;
		if (!device.isEmpty()) {
			deviceMatches = device.contains(timeseries.getDevice());
		}
		if (!location.isEmpty()) {
			locatioMatches = location.contains(timeseries.getLocation());
		}
		if (!symName.isEmpty()) {
			symbolicNameMatches = symName.contains(timeseries.getSymbolicName());
		}
		return deviceMatches && locatioMatches && symbolicNameMatches;
	}

	public InputStream exportTimeseries(long start, long end, String database, List<Timeseries> timeseries,
			AggregateFunction function, Long groupBy, Set<String> devicesFilterSet, Set<String> locationsFilterSet,
			Set<String> symbolicNameFilterSet) throws IOException {
		var payload = getTimeseriesList(start, end, database, timeseries, function, groupBy, devicesFilterSet,
				locationsFilterSet, symbolicNameFilterSet);
		var stream = csvConverter.convertToCsv(payload);
		return stream;
	}

}
