package de.dlr.shepard.context.semantic.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.semantic.entities.Predicate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * SEMA-V6-002 — unit tests for {@link PredicateDAO}.
 */
class PredicateDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private PredicateDAO dao;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Predicate makePredicate(String appId, String label, String vocabularyAppId, boolean required) {
    Predicate p = new Predicate();
    p.setAppId(appId);
    p.setLabel(label);
    p.setUri("http://example.org/" + appId);
    p.setVocabularyAppId(vocabularyAppId);
    p.setExpectedObjectType(Predicate.ExpectedObjectType.LITERAL.name());
    p.setCardinality(Predicate.Cardinality.MANY.name());
    p.setRequired(required);
    return p;
  }

  // ─── getEntityType ────────────────────────────────────────────────────────

  @Test
  void getEntityTypeReturnsPredicateClass() {
    assertEquals(Predicate.class, dao.getEntityType());
  }

  // ─── findByAppId ─────────────────────────────────────────────────────────

  @Test
  void findByAppIdReturnsPredicateWhenFound() {
    Predicate p = makePredicate("p-001", "Creator", "vocab-001", false);
    when(session.loadAll(eq(Predicate.class), any(Filter.class), eq(1))).thenReturn(List.of(p));

    Predicate result = dao.findByAppId("p-001");

    assertEquals(p, result);
  }

  @Test
  void findByAppIdReturnsNullWhenNotFound() {
    when(session.loadAll(eq(Predicate.class), any(Filter.class), eq(1))).thenReturn(List.of());

    Predicate result = dao.findByAppId("missing");

    assertNull(result);
  }

  @Test
  void findByAppIdReturnsNullForNullInput() {
    Predicate result = dao.findByAppId(null);
    assertNull(result);
  }

  // ─── listAll ─────────────────────────────────────────────────────────────

  @Test
  void listAllReturnsSortedByLabelCaseInsensitive() {
    Predicate z = makePredicate("p3", "title", "v1", false);
    Predicate a = makePredicate("p1", "Creator", "v1", false);
    Predicate m = makePredicate("p2", "license", "v1", false);
    when(session.loadAll(eq(Predicate.class), eq(1))).thenReturn(List.of(z, a, m));

    List<Predicate> result = dao.listAll();

    assertEquals(3, result.size());
    assertEquals("Creator", result.get(0).getLabel());
    assertEquals("license", result.get(1).getLabel());
    assertEquals("title",   result.get(2).getLabel());
  }

  // ─── listRequired ────────────────────────────────────────────────────────

  @Test
  void listRequiredReturnsOnlyRequiredPredicates() {
    Predicate req    = makePredicate("r1", "Title (required)", "v1", true);
    Predicate notReq = makePredicate("n1", "Description", "v1", false);
    when(session.loadAll(eq(Predicate.class), eq(1))).thenReturn(List.of(req, notReq));

    List<Predicate> result = dao.listRequired();

    assertEquals(1, result.size());
    assertEquals("r1", result.get(0).getAppId());
  }

  @Test
  void listRequiredReturnsEmptyWhenNoneRequired() {
    Predicate p1 = makePredicate("a1", "A", "v1", false);
    Predicate p2 = makePredicate("a2", "B", "v1", false);
    when(session.loadAll(eq(Predicate.class), eq(1))).thenReturn(List.of(p1, p2));

    List<Predicate> result = dao.listRequired();

    assertTrue(result.isEmpty());
  }

  // ─── enum name() storage contract ────────────────────────────────────────

  @Test
  void enumFieldsStoredAsNameStrings() {
    Predicate p = makePredicate("x1", "label", "v1", false);
    p.setExpectedObjectType(Predicate.ExpectedObjectType.URI.name());
    p.setCardinality(Predicate.Cardinality.ONE.name());

    assertEquals("URI", p.getExpectedObjectType());
    assertEquals("ONE", p.getCardinality());
  }
}
