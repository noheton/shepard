package de.dlr.shepard.neo4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.exception.ConnectionException;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

public class NeoConnectorTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private SessionFactory sessionFactory;

  @InjectMocks
  private NeoConnector neoConnector;

  @Test
  public void testAlive() {
    var result = mock(Result.class);
    @SuppressWarnings("unchecked")
    Iterator<Map<String, Object>> iterator = mock(Iterator.class);
    when(result.iterator()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(true);
    when(iterator.next()).thenReturn(Map.of("count", 123));
    when(sessionFactory.openSession()).thenReturn(session);
    when(session.query("MATCH (n) RETURN count(*) as count", Collections.emptyMap())).thenReturn(result);

    var actual = neoConnector.alive();
    assertTrue(actual);
  }

  @Test
  public void testAliveException() {
    when(sessionFactory.openSession()).thenThrow(new ConnectionException("Exception", new IOException()));

    var actual = neoConnector.alive();
    assertFalse(actual);
  }

  @Test
  public void testAliveEmpty() {
    var result = mock(Result.class);
    @SuppressWarnings("unchecked")
    Iterator<Map<String, Object>> iterator = mock(Iterator.class);
    when(result.iterator()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(false);
    when(sessionFactory.openSession()).thenReturn(session);
    when(session.query("MATCH (n) RETURN count(*) as count", Collections.emptyMap())).thenReturn(result);

    var actual = neoConnector.alive();
    assertFalse(actual);
  }

  @Test
  public void testAliveWrong() {
    var result = mock(Result.class);
    @SuppressWarnings("unchecked")
    Iterator<Map<String, Object>> iterator = mock(Iterator.class);
    when(result.iterator()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(true);
    when(iterator.next()).thenReturn(Map.of("test", 123));
    when(sessionFactory.openSession()).thenReturn(session);
    when(session.query("MATCH (n) RETURN count(*) as count", Collections.emptyMap())).thenReturn(result);

    var actual = neoConnector.alive();
    assertFalse(actual);
  }
}
