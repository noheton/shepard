package de.dlr.shepard.common.neo4j.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class BasicEntityDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private BasicEntityDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(BasicEntity.class, type);
  }
}
