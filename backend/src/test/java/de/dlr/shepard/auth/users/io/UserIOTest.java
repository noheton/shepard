package de.dlr.shepard.auth.users.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserIOTest {

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
    assertEquals("[%s]".formatted(key.getUid()), Arrays.toString(converted.getApiKeyIds()));
    assertEquals(user.getEmail(), converted.getEmail());
    assertEquals(user.getFirstName(), converted.getFirstName());
    assertEquals(user.getLastName(), converted.getLastName());
    assertEquals("[2]", Arrays.toString(converted.getSubscriptionIds()));
  }
}
