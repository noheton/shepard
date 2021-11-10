package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
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
public class Permissions implements HasId {

	@Id
	@GeneratedValue
	private Long id;

	@Relationship(type = Constants.HAS_PERMISSIONS, direction = Relationship.INCOMING)
	private AbstractEntity entity;

	@Relationship(type = Constants.OWNED_BY)
	private User owner;

	@ToString.Exclude
	@Relationship(type = Constants.READABLE_BY)
	private List<User> reader = new ArrayList<>();

	@ToString.Exclude
	@Relationship(type = Constants.WRITEABLE_BY)
	private List<User> writer = new ArrayList<>();

	@ToString.Exclude
	@Relationship(type = Constants.MANAGEABLE_BY)
	private List<User> manager = new ArrayList<>();

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public Permissions(long id) {
		this.id = id;
	}

	public Permissions(AbstractEntity entity, User owner) {
		this.entity = entity;
		this.owner = owner;
	}

	public Permissions(User owner, List<User> reader, List<User> writer, List<User> manager) {
		this.owner = owner;
		this.reader = reader;
		this.writer = writer;
		this.manager = manager;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(id);
		result = prime * result + HasId.hashcodeHelper(entity);
		result = prime * result + HasId.hashcodeHelper(owner);
		result = prime * result + HasId.hashcodeHelper(reader);
		result = prime * result + HasId.hashcodeHelper(writer);
		result = prime * result + HasId.hashcodeHelper(manager);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Permissions))
			return false;
		Permissions other = (Permissions) obj;
		return Objects.equals(id, other.id) && HasId.equalsHelper(entity, other.entity)
				&& HasId.equalsHelper(owner, other.owner) && HasId.equalsHelper(reader, other.reader)
				&& HasId.equalsHelper(writer, other.writer) && HasId.equalsHelper(manager, other.manager);
	}

	@Override
	public String getUniqueId() {
		return id.toString();
	}

}
