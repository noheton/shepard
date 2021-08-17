package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "TimeseriesReference")
public class TimeseriesReferenceIO extends BasicReferenceIO {

	private long start;

	private long end;

	private Timeseries[] timeseries;

	private long timeseriesContainerId;

	public TimeseriesReferenceIO(TimeseriesReference ref) {
		super(ref);
		this.start = ref.getStart();
		this.end = ref.getEnd();
		this.timeseries = ref.getTimeseries().toArray(Timeseries[]::new);
		this.timeseriesContainerId = ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getId() : -1;
	}

}
