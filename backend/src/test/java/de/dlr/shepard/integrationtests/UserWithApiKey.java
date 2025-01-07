package de.dlr.shepard.integrationtests;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import lombok.Value;

@Value
public class UserWithApiKey {

  private User user;
  private ApiKey apiKey;
}
