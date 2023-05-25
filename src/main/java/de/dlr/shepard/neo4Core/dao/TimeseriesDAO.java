package de.dlr.shepard.neo4Core.dao;

import java.util.Map;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.util.CypherQueryHelper;

public class TimeseriesDAO extends GenericDAO<Timeseries> {

	/**
	 * Find a timeseries by properties
	 *
	 * @param measurement  measurement
	 * @param device       device
	 * @param location     location
	 * @param symbolicName symbolicName
	 * @param field        field
	 *
	 * @return the found timeseries or null
	 */
	public Timeseries find(String measurement, String device, String location, String symbolicName, String field) {
		var query = String.format(
				"MATCH (t:Timeseries { measurement: $measurement, device: $device, location: $location, symbolicName: $symbolicName, field: $field }) %s",
				CypherQueryHelper.getReturnPart("t"));
		Map<String, Object> params = Map.of("measurement", measurement, "device", device, "location", location,
				"symbolicName", symbolicName, "field", field);
		var results = findByQuery(query, params);
		return results.iterator().hasNext() ? results.iterator().next() : null;
	}

	@Override
	public Class<Timeseries> getEntityType() {
		return Timeseries.class;
	}
}
