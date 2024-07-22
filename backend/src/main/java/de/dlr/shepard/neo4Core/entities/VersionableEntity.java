package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class VersionableEntity extends BasicEntity {
	@Index
	private Long shepardId;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	protected VersionableEntity(long id) {
		super(id);
	}

	@Override
	public long getNumericId() {
		return getShepardId();
	}
}
