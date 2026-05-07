package de.dlr.shepard.auth.apikey.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ApiKeyTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(ApiKey.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      // appId is L2a-additive; not part of equals (uid remains canonical).
      .withIgnoredFields("appId")
      .verify();
  }

  @Test
  public void reducedConstructorTest() {
    var user = new User("bob");
    var date = new Date();
    var apiKey = new ApiKey();
    apiKey.setBelongsTo(user);
    apiKey.setCreatedAt(date);
    apiKey.setName("MyApiKey");

    var actual = new ApiKey("MyApiKey", date, user);
    assertEquals(apiKey, actual);
  }
}
