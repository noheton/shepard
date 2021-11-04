package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@NoArgsConstructor
public class User implements HasId {

	@Id
	private String username;

	private String firstName;

	private String lastName;

	private String email;

	@ToString.Exclude
	@Relationship(type = Constants.SUBSCRIBED_BY, direction = Relationship.INCOMING)
	private List<Subscription> subscriptions = new ArrayList<Subscription>();

	@ToString.Exclude
	@Relationship(type = Constants.BELONGS_TO, direction = Relationship.INCOMING)
	private List<ApiKey> apiKeys = new ArrayList<ApiKey>();

	/**
	 * For testing purposes only
	 *
	 * @param username identifies the user
	 */
	public User(String username) {
		this.username = username;
	}

	/**
	 * Simple constructor
	 *
	 * @param username  Username
	 * @param firstName First name
	 * @param lastName  Last name
	 * @param email     E-Mail Address
	 */
	public User(String username, String firstName, String lastName, String email) {
		this.username = username;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(email, firstName, lastName, username);
		result = prime * result + HasId.hashcodeHelper(apiKeys);
		result = prime * result + HasId.hashcodeHelper(subscriptions);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof User))
			return false;
		User other = (User) obj;
		return HasId.equalsHelper(apiKeys, other.apiKeys) && HasId.equalsHelper(subscriptions, other.subscriptions)
				&& Objects.equals(email, other.email) && Objects.equals(firstName, other.firstName)
				&& Objects.equals(lastName, other.lastName) && Objects.equals(username, other.username);
	}

	@Override
	public String getUniqueId() {
		return username;
	}
}
