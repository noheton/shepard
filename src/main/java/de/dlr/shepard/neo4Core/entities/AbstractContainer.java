package de.dlr.shepard.neo4Core.entities;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString
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
