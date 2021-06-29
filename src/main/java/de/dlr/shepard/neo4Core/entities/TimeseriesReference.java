package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.neo4Core.converter.TimeseriesConverter;
import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class TimeseriesReference extends BasicReference {

	private long start;

	private long end;

	@Property("timeseriesJson")
	@Convert(TimeseriesConverter.class)
	private List<Timeseries> timeseries = new ArrayList<>();

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

	public void addTimeseries(Timeseries timeseries) {
		this.timeseries.add(timeseries);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(end, start, timeseries);
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
		return end == other.end && start == other.start && Objects.equals(timeseries, other.timeseries)
				&& HasId.equalsHelper(timeseriesContainer, other.timeseriesContainer);
	}

}
