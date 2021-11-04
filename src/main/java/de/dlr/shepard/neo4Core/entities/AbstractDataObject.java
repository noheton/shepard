package de.dlr.shepard.neo4Core.entities;

import java.util.Map;
import java.util.Objects;

import org.neo4j.ogm.annotation.Properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public abstract class AbstractDataObject extends AbstractEntity {

	private String name;

	private String description;

	@ToString.Exclude
	@Properties
	private Map<String, String> attributes;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public AbstractDataObject(long id) {
		super(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(attributes, description, name);
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
				&& Objects.equals(name, other.name);
	}

}
