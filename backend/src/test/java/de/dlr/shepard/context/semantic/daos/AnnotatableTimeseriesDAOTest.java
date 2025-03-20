package de.dlr.shepard.context.semantic.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class AnnotatableTimeseriesDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private AnnotatableTimeseriesDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(AnnotatableTimeseries.class, type);
  }
}
