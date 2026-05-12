package de.dlr.shepard.auth.users.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var credA = new GitCredential();
    credA.setAppId("cred-a");
    var credB = new GitCredential();
    credB.setAppId("cred-b");
    EqualsVerifier.simple()
      .forClass(User.class)
      .withPrefabValues(ApiKey.class, new ApiKey(a), new ApiKey(b))
      .withPrefabValues(Subscription.class, new Subscription(1L), new Subscription(2L))
      .withPrefabValues(GitCredential.class, credA, credB)
      // appId is L2a-additive; not part of equals (legacy id remains canonical).
      .withIgnoredFields("appId")
      .verify();
  }

  @Test
  public void getUniqueIdTest() {
    var user = new User("bob");
    assertEquals("bob", user.getUniqueId());
  }

  @Test
  public void simpleConstructorTest() {
    var user = new User() {
      {
        setEmail("john.doe@example.com");
        setFirstName("John");
        setLastName("Doe");
        setUsername("bob");
      }
    };

    var actual = new User("bob", "John", "Doe", "john.doe@example.com");
    assertEquals(user, actual);
  }
}
