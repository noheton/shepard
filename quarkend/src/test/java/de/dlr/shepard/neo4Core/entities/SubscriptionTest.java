package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class SubscriptionTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(Subscription.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .verify();
  }

  @Test
  public void getUniqueIdTest() {
    var sub = new Subscription(2L);
    assertEquals("2", sub.getUniqueId());
  }
}
