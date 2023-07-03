package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.NodeEntity;

import de.dlr.shepard.semantics.SemanticRepositoryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SemanticRepository extends BasicEntity {

	private SemanticRepositoryType type;

	private String endpoint;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public SemanticRepository(long id) {
		super(id);
	}

}
