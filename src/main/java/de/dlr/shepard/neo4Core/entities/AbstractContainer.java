package de.dlr.shepard.neo4Core.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class AbstractContainer extends AbstractEntity {

	private String name;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public AbstractContainer(long id) {
		super(id);
	}
}
