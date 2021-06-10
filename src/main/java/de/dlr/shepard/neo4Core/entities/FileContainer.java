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
public class FileContainer extends AbstractContainer {

	private String mongoId;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public FileContainer(long id) {
		super(id);
	}

}
