package de.dlr.shepard.neo4Core.entities;

import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NodeEntity
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public class BasicReference extends AbstractEntity {

	private String name;

	@ToString.Exclude
	@Relationship(type = Constants.HAS_REFERENCE, direction = Relationship.INCOMING)
	private DataObject dataObject;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public BasicReference(long id) {
		super(id);
	}

	/**
	 * Returns the name of the implemented class
	 * 
	 * @return the simple class name
	 */
	public String getType() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(name);
		result = prime * result + HasId.hashcodeHelper(dataObject);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof BasicReference))
			return false;
		BasicReference other = (BasicReference) obj;
		return HasId.equalsHelper(dataObject, other.dataObject) && Objects.equals(name, other.name);
	}
}
