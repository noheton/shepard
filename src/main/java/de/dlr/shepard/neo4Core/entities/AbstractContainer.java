package de.dlr.shepard.neo4Core.entities;

import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
public abstract class AbstractContainer extends AbstractEntity {

	@ToString.Exclude
	@Relationship(type = Constants.HAS_PERMISSIONS)
	private Permissions permissions;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	protected AbstractContainer(long id) {
		super(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + HasId.hashcodeHelper(permissions);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof AbstractContainer))
			return false;
		AbstractContainer other = (AbstractContainer) obj;
		return HasId.equalsHelper(permissions, other.permissions);
	}

}
