package de.dlr.shepard.publish.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.publish.entities.Publication;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

class PublicationDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private PublicationDAO dao = new PublicationDAO();

  private Publication publication() {
    Publication p = new Publication();
    p.setAppId("pub-1");
    p.setPid("mock:shepard:data-objects:01HF-A:1747000000000");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("mock");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    return p;
  }

  @Test
  void entityTypeIsPublication() {
    assertEquals(Publication.class, dao.getEntityType());
  }

  @Test
  void findByPidHits() {
    Publication p = publication();
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(List.of(p));
    var out = dao.findByPid(p.getPid());
    assertTrue(out.isPresent());
    assertSame(p, out.get());
  }

  @Test
  void findByPidMisses() {
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(Collections.emptyList());
    assertFalse(dao.findByPid("nope").isPresent());
  }

  @Test
  void findByPidRejectsBlankInputEarly() {
    // null + blank short-circuit before the session is hit.
    assertFalse(dao.findByPid(null).isPresent());
    assertFalse(dao.findByPid("").isPresent());
    assertFalse(dao.findByPid("   ").isPresent());
    verify(session, never()).query(eq(Publication.class), any(String.class), anyMap());
  }

  @Test
  void findByEntityAppIdReturnsList() {
    Publication a = publication();
    Publication b = publication();
    b.setAppId("pub-2");
    b.setPid("mock:shepard:data-objects:01HF-A:1700000000000");
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(List.of(a, b));
    var out = dao.findByEntityAppId("01HF-A");
    assertEquals(2, out.size());
    assertSame(a, out.get(0));
    assertSame(b, out.get(1));
  }

  @Test
  void findByEntityAppIdRejectsBlank() {
    assertTrue(dao.findByEntityAppId(null).isEmpty());
    assertTrue(dao.findByEntityAppId("").isEmpty());
    verify(session, never()).query(eq(Publication.class), any(String.class), anyMap());
  }

  @Test
  void attachToEntityRejectsNullPublication() {
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(null, "01HF-A"));
  }

  @Test
  void attachToEntityRejectsBlankEntityAppId() {
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(publication(), ""));
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(publication(), null));
  }

  @Test
  void attachToEntityPersistsAndAttachesEdge() {
    Publication p = new Publication();
    p.setPid("pid-x");
    p.setMintedAt(1L);
    p.setMinterId("mock");
    p.setEntityKind("data-objects");
    // Stub the MERGE-edge runQuery: GenericDAO#runQuery returns a
    // boolean derived from queryStatistics.containsUpdates().
    Result mergeResult = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(mergeResult.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(mergeResult);

    Publication saved = dao.attachToEntity(p, "01HF-A");

    assertEquals("01HF-A", saved.getEntityAppId());
    // GenericDAO#createOrUpdate mints an appId because Publication implements HasAppId.
    assertTrue(saved.getAppId() != null && !saved.getAppId().isBlank());
    // Session.save must have happened (createOrUpdate path).
    verify(session, times(1)).save(eq(p), eq(1));
    // The MERGE edge query is invoked with the right params.
    verify(session, times(1)).query(any(String.class), eq(Map.of("entityAppId", "01HF-A", "pubAppId", saved.getAppId())));
  }

  @Test
  void hasPublicationConstantValue() {
    assertEquals("HAS_PUBLICATION", PublicationDAO.HAS_PUBLICATION);
  }
}
