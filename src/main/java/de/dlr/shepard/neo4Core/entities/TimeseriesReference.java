package de.dlr.shepard.neo4Core.entities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@NodeEntity
@Getter
@Setter
@ToString
@Log4j2
@NoArgsConstructor
public class TimeseriesReference extends BasicReference {

	private long start;

	private long end;

	private List<String> timeseriesJson = new ArrayList<>();

	@ToString.Exclude
	@Relationship(type = Constants.IS_IN_CONTAINER)
	private TimeseriesContainer timeseriesContainer;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public TimeseriesReference(long id) {
		super(id);
	}

	public List<Timeseries> getTimeseries() {
		var mapper = new ObjectMapper();
		TypeReference<Timeseries> t = new TypeReference<Timeseries>() {
		};
		List<Timeseries> result = new ArrayList<>();
		for (var ts : timeseriesJson) {
			try {
				result.add(mapper.readValue(ts, t));
			} catch (IOException e) {
				log.error("Could not convert timeseries", ts);
				result = new ArrayList<Timeseries>();
			}
		}
		return result;
	}

	public void setTimeseries(List<Timeseries> timeseries) {
		List<String> result = new ArrayList<>();
		var mapper = new ObjectMapper();
		for (var ts : timeseries) {
			try {
				result.add(mapper.writeValueAsString(ts));
			} catch (JsonProcessingException e) {
				log.error("Could not convert timeseries", ts);
			}
		}
		timeseriesJson = result;
	}

	public void addTimeSeries(Timeseries timeseries) {
		var mapper = new ObjectMapper();
		try {
			timeseriesJson.add(mapper.writeValueAsString(timeseries));
		} catch (JsonProcessingException e) {
			log.error("Could not convert timeseries", timeseries);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(end, start, timeseriesJson);
		result = prime * result + HasId.hashcodeHelper(timeseriesContainer);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof TimeseriesReference))
			return false;
		TimeseriesReference other = (TimeseriesReference) obj;
		return end == other.end && start == other.start && Objects.equals(timeseriesJson, other.timeseriesJson)
				&& HasId.equalsHelper(timeseriesContainer, other.timeseriesContainer);
	}

}
