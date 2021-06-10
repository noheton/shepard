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
@ToString
@NoArgsConstructor
public class DataObjectReference extends BasicReference {

	@ToString.Exclude
	@Relationship(type = Constants.POINTS_TO)
	private AbstractDataObject referencedDataObject;

	private String relationship;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public DataObjectReference(long id) {
		super(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(relationship);
		result = prime * result + HasId.hashcodeHelper(referencedDataObject);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof DataObjectReference))
			return false;
		DataObjectReference other = (DataObjectReference) obj;
		return Objects.equals(relationship, other.relationship)
				&& HasId.equalsHelper(referencedDataObject, other.referencedDataObject);
	}

}
