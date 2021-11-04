package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.NodeEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TimeseriesContainer extends AbstractContainer {

	private String database;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public TimeseriesContainer(long id) {
		super(id);
	}

}
