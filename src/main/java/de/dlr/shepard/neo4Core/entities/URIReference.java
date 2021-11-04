package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.NodeEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class URIReference extends BasicReference {

	private String uri;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public URIReference(long id) {
		super(id);
	}

}
