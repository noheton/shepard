package de.dlr.shepard.neo4Core.entities;

import java.util.Map;

import org.neo4j.ogm.annotation.Properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractDataObject extends VersionableEntity {

	private String description;

	@ToString.Exclude
	@Properties
	private Map<String, String> attributes;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	protected AbstractDataObject(long id) {
		super(id);
	}

}
