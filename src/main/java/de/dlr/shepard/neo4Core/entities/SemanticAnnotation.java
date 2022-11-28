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
public class SemanticAnnotation extends AbstractEntity {

	private String propertyIRI;

	private String valueIRI;

	@ToString.Exclude
	@Relationship(type = Constants.PROPERTY_REPOSITORY)
	private SemanticRepository propertyRepository;

	@ToString.Exclude
	@Relationship(type = Constants.VALUE_REPOSITORY)
	private SemanticRepository valueRepository;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public SemanticAnnotation(long id) {
		super(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(propertyIRI, valueIRI);
		result = prime * result + HasId.hashcodeHelper(propertyRepository);
		result = prime * result + HasId.hashcodeHelper(valueRepository);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof SemanticAnnotation))
			return false;
		SemanticAnnotation other = (SemanticAnnotation) obj;
		return Objects.equals(propertyIRI, other.propertyIRI) && Objects.equals(valueIRI, other.valueIRI)
				&& HasId.equalsHelper(propertyRepository, other.propertyRepository)
				&& HasId.equalsHelper(valueRepository, other.valueRepository);
	}
}
