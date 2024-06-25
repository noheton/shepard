package de.dlr.shepard.integrationtests;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import lombok.Value;

@Value
public class UserWithApiKey {
	private User user;
	private ApiKey apiKey;
}
