package de.dlr.shepard.neo4Core.converter;

import de.dlr.shepard.influxDB.Timeseries;

public class TimeseriesConverter extends JsonListConverter<Timeseries> {

	@Override
	Class<Timeseries> getEntityType() {
		return Timeseries.class;
	}

}
