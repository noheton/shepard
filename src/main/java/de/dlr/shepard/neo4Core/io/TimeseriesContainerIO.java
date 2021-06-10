package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "TimeseriesContainer")
public class TimeseriesContainerIO extends AbstractContainerIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String database;

	public TimeseriesContainerIO(TimeseriesContainer container) {
		super(container);
		this.database = container.getDatabase();
	}

}
