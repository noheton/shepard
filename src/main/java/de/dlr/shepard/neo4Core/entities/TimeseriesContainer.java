package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.NodeEntity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NodeEntity
@Getter
@Setter
@ToString
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
