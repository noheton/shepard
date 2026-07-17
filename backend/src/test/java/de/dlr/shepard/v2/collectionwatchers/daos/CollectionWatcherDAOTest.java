package de.dlr.shepard.v2.collectionwatchers.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public class CollectionWatcherDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private CollectionWatcherDAO dao = new CollectionWatcherDAO();

  // APISIMP-MEREST-WATCHED-COUNT

  @Test
  public void countByUsername_returnsCountFromCypher() {
    String query =
        "MATCH (w:CollectionWatcher) " +
        "WHERE w.username = $username " +
        "RETURN count(w) AS cnt";
    Map<String, Object> row = Map.of("cnt", 3L);
    Result result = mock(Result.class);
    when(result.iterator()).thenReturn(List.<Map<String, Object>>of(row).iterator());
    when(session.query(query, Map.of("username", "alice"))).thenReturn(result);

    assertEquals(3L, dao.countByUsername("alice"));
  }

  @Test
  public void countByUsername_emptyResult_returnsZero() {
    String query =
        "MATCH (w:CollectionWatcher) " +
        "WHERE w.username = $username " +
        "RETURN count(w) AS cnt";
    Result result = mock(Result.class);
    when(result.iterator()).thenReturn(Collections.emptyIterator());
    when(session.query(query, Map.of("username", "bob"))).thenReturn(result);

    assertEquals(0L, dao.countByUsername("bob"));
  }
}
