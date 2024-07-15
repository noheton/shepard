package de.dlr.shepard.neo4Core.entities;

import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@NoArgsConstructor
public class CollectionReference extends BasicReference {

	@ToString.Exclude
	@Relationship(type = Constants.POINTS_TO)
	private Collection referencedCollection;

	private String relationship;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public CollectionReference(long id) {
		super(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(relationship);
		result = prime * result + HasId.hashcodeHelper(referencedCollection);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof CollectionReference))
			return false;
		CollectionReference other = (CollectionReference) obj;
		return Objects.equals(relationship, other.relationship)
				&& HasId.equalsHelper(referencedCollection, other.referencedCollection);
	}

}
