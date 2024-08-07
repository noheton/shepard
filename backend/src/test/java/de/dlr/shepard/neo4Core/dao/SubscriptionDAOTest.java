package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Subscription;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class SubscriptionDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private SubscriptionDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(Subscription.class, type);
  }
}
