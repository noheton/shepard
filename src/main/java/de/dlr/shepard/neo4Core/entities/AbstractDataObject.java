package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.neo4j.ogm.annotation.Properties;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public abstract class AbstractDataObject extends AbstractEntity {

	private String name;

	private String description;

	@ToString.Exclude
	@Properties
	private Map<String, String> attributes;

	@ToString.Exclude
	@Relationship(type = Constants.POINTS_TO, direction = Relationship.INCOMING)
	private List<DataObjectReference> incoming = new ArrayList<DataObjectReference>();

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public AbstractDataObject(long id) {
		super(id);
	}

	/**
	 * Add one incoming DataObjectReference
	 * 
	 * @param dataObjectReference the dataObjectReference to add
	 */
	public void addIncoming(DataObjectReference dataObjectReference) {
		incoming.add(dataObjectReference);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(attributes, description, name);
		result = prime * result + HasId.hashcodeHelper(incoming);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof AbstractDataObject))
			return false;
		AbstractDataObject other = (AbstractDataObject) obj;
		return Objects.equals(attributes, other.attributes) && Objects.equals(description, other.description)
				&& Objects.equals(name, other.name) && HasId.equalsHelper(incoming, other.incoming);
	}

}
