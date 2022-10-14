package de.dlr.shepard.influxDB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.dlr.shepard.exceptions.InvalidBodyException;

public class TimeseriesService {

	private InfluxDBConnector influxConnector = InfluxDBConnector.getInstance();
	private CsvConverter csvConverter = new CsvConverter();

	/**
	 * Creates timeseries and writes them to influxDB
	 *
	 * @param database The database to be queried
	 * @param payload  the Timeseries with InfluxPoints to be created
	 * @return An error if there was a problem, empty string if all went well
	 */
	public String createTimeseries(String database, TimeseriesPayload payload) {
		String sanityCheck = InfluxUtil.sanitize(payload.getTimeseries());
		if (sanityCheck.length() != 0)
			throw new InvalidBodyException(sanityCheck);
		if (!influxConnector.databaseExist(database)) {
			return String.format("The database %s does not exist", database);
		}
		return influxConnector.saveTimeseriesPayload(database, payload);
	}

	/**
	 * Queries the database for timeseries including aggregate functions.
	 *
	 * @param startTimeStamp The beginning of the timeseries
	 * @param endTimeStamp   The end of the timeseries
	 * @param database       The database to be queried
	 * @param timeseries     The timeseries whose points are queried
	 * @param function       The aggregate function
	 * @param groupBy        The time interval measurements get grouped by
	 * @param fillOption     The fill option for missing values
	 * @return timeseries with influx points
	 */
	public TimeseriesPayload getTimeseriesPayload(long startTimeStamp, long endTimeStamp, String database,
			Timeseries timeseries, SingleValuedUnaryFunction function, Long groupBy, FillOption fillOption) {
		TimeseriesPayload payload = influxConnector.getTimeseriesPayload(startTimeStamp, endTimeStamp, database, timeseries,
				function, groupBy, fillOption);
		return payload;
	}

	/**
	 * Queries the database for many timeseries in parallel. Returns a list of
	 * timeseries. If the filter sets are empty, no filtering takes place.
	 *
	 * @param start                 The beginning of the timeseries
	 * @param end                   The end of the timeseries
	 * @param database              The database to be queried
	 * @param timeseriesList        The list of timeseries whose points are queried
	 * @param function              The aggregate function
	 * @param groupBy               The time interval measurements get grouped by
	 * @param fillOption            The fill option for missing values
	 * @param devicesFilterSet      A set of allowed devices or an empty set
	 * @param locationsFilterSet    A set of allowed locations or an empty set
	 * @param symbolicNameFilterSet A set of allowed symbolic names or an empty set
	 * @return a list of timeseries with influx points
	 */
	public List<TimeseriesPayload> getTimeseriesPayloadList(long start, long end, String database,
			List<Timeseries> timeseriesList, SingleValuedUnaryFunction function, Long groupBy, FillOption fillOption,
			Set<String> devicesFilterSet, Set<String> locationsFilterSet, Set<String> symbolicNameFilterSet) {
		var timeseriesPayloadQueue = new ConcurrentLinkedQueue<TimeseriesPayload>();
		timeseriesList.parallelStream().forEach(timeseries -> {
			TimeseriesPayload payload = null;
			if (matchFilter(timeseries, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet)) {
				payload = getTimeseriesPayload(start, end, database, timeseries, function, groupBy, fillOption);
			}
			if (payload != null) {
				timeseriesPayloadQueue.add(payload);
			}

		});
		return new ArrayList<>(timeseriesPayloadQueue);
	}

	/**
	 * Returns a list of timeseries objects that are in the given database.
	 *
	 * @param database the given database
	 * @return a list of timeseries objects
	 */
	public List<Timeseries> getTimeseriesAvailable(String database) {
		return influxConnector.getTimeseriesAvailable(database);
	}

	/**
	 * Export timeseries as CSV File. If the filter sets are empty, no filtering
	 * takes place.
	 *
	 * @param start                 The beginning of the timeseries
	 * @param end                   The end of the timeseries
	 * @param database              The database to be queried
	 * @param timeseriesList        The list of timeseries whose points are queried
	 * @param function              The aggregate function
	 * @param groupBy               The time interval measurements get grouped by
	 * @param fillOption            The fill option for missing values
	 * @param devicesFilterSet      A set of allowed devices or an empty set
	 * @param locationsFilterSet    A set of allowed locations or an empty set
	 * @param symbolicNameFilterSet A set of allowed symbolic names or an empty set
	 * @return InputStream containing the CSV file
	 * @throws IOException When the CSV file could not be written
	 */
	public InputStream exportTimeseriesPayload(long start, long end, String database, List<Timeseries> timeseriesList,
			SingleValuedUnaryFunction function, Long groupBy, FillOption fillOption, Set<String> devicesFilterSet,
			Set<String> locationsFilterSet, Set<String> symbolicNameFilterSet) throws IOException {
		var payload = getTimeseriesPayloadList(start, end, database, timeseriesList, function, groupBy, fillOption,
				devicesFilterSet, locationsFilterSet, symbolicNameFilterSet);
		var stream = csvConverter.convertToCsv(payload);
		return stream;
	}

	/**
	 * Import multiple timeseries from a CSV file
	 *
	 * @param database The database to write to
	 * @param stream   The InputStream containing the CSV file
	 * @return An error if there was a problem, empty string if all went well
	 * @throws IOException If the CSV file could not be read
	 */
	public String importTimeseries(String database, InputStream stream) throws IOException {
		List<String> errors = new ArrayList<>();
		var timeseriesList = csvConverter.convertToPayload(stream);
		for (var timeseries : timeseriesList) {
			var error = createTimeseries(database, timeseries);
			if (!error.isBlank()) {
				errors.add(error);
			}
		}
		return String.join(", ", errors);
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

}
