package de.dlr.shepard.template.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

/**
 * APISIMP-TEMPLATE-TAGS-INMEM — unit tests for the DB-side pagination
 * methods added to {@link ShepardTemplateDAO}: {@code countDistinctTags}
 * and {@code listDistinctTagsPaged}. The methods must delegate to the
 * Neo4j session; the REST layer must not materialise all tags in memory.
 */
class ShepardTemplateDAOTagsPaginationTest extends BaseTestCase {

  @Mock
  Session session;

  @InjectMocks
  ShepardTemplateDAO dao;

  // ── countDistinctTags ────────────────────────────────────────────────

  @Test
  void countDistinctTagsReturnsZeroWhenQueryResultEmpty() {
    var result = mock(org.neo4j.ogm.model.Result.class);
    when(result.queryResults()).thenReturn(List.of());
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    long count = dao.countDistinctTags(null);

    assertEquals(0L, count);
  }

  @Test
  void countDistinctTagsReturnsValueFromDb() {
    var result = mock(org.neo4j.ogm.model.Result.class);
    when(result.queryResults()).thenReturn(List.of(Map.of("total", 17L)));
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    long count = dao.countDistinctTags(null);

    assertEquals(17L, count);
    verify(session).query(any(String.class), anyMap());
  }

  // ── listDistinctTagsPaged ────────────────────────────────────────────

  @Test
  void listDistinctTagsPagedReturnsEmptyListWhenNoRows() {
    var result = mock(org.neo4j.ogm.model.Result.class);
    when(result.queryResults()).thenReturn(List.of());
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    List<String> tags = dao.listDistinctTagsPaged(null, 0, 50);

    assertNotNull(tags);
    assertEquals(0, tags.size());
  }

  @Test
  void listDistinctTagsPagedReturnsTagsFromDb() {
    var result = mock(org.neo4j.ogm.model.Result.class);
    when(result.queryResults()).thenReturn(
        List.of(Map.of("tag", "calibration"), Map.of("tag", "hot-fire"), Map.of("tag", "lumen")));
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    List<String> tags = dao.listDistinctTagsPaged(null, 0, 50);

    assertEquals(3, tags.size());
    assertEquals("calibration", tags.get(0));
    assertEquals("hot-fire", tags.get(1));
    assertEquals("lumen", tags.get(2));
    verify(session).query(any(String.class), anyMap());
  }
}
