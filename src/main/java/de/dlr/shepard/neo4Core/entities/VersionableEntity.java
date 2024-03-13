package de.dlr.shepard.neo4Core.entities;

import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class VersionableEntity extends BasicEntity {

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
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(shepardId);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof VersionableEntity))
			return false;
		if (!super.equals(obj))
			return false;
		VersionableEntity other = (VersionableEntity) obj;
		return Objects.equals(shepardId, other.shepardId);
	}

}
