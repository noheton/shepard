package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserIOTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(UserIO.class).verify();
  }

  @Test
  public void testConversion() {
    var key = new ApiKey(UUID.randomUUID());
    var sub = new Subscription(2L);

    var user = new User("bob");
    user.setApiKeys(List.of(key));
    user.setEmail("Email");
    user.setFirstName("name");
    user.setLastName("last");
    user.setSubscriptions(List.of(sub));

    var converted = new UserIO(user);
    assertEquals(user.getUsername(), converted.getUsername());
    assertEquals(String.format("[%s]", key.getUid()), Arrays.toString(converted.getApiKeyIds()));
    assertEquals(user.getEmail(), converted.getEmail());
    assertEquals(user.getFirstName(), converted.getFirstName());
    assertEquals(user.getLastName(), converted.getLastName());
    assertEquals("[2]", Arrays.toString(converted.getSubscriptionIds()));
  }
}
