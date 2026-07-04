package de.dlr.shepard.context.semantic.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * SEMA-V6-002 — unit tests for {@link VocabularyDAO}.
 */
class VocabularyDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private VocabularyDAO dao;

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Vocabulary makeVocab(String appId, String label, boolean enabled) {
    Vocabulary v = new Vocabulary();
    v.setAppId(appId);
    v.setLabel(label);
    v.setUri("http://example.org/" + appId + "/");
    v.setEnabled(enabled);
    return v;
  }

  // ─── getEntityType ────────────────────────────────────────────────────────

  @Test
  void getEntityTypeReturnsVocabularyClass() {
    assertEquals(Vocabulary.class, dao.getEntityType());
  }

  // ─── findByAppId ─────────────────────────────────────────────────────────

  @Test
  void findByAppIdReturnsVocabularyWhenFound() {
    Vocabulary v = makeVocab("appid-001", "Dublin Core", true);
    when(session.loadAll(eq(Vocabulary.class), any(Filter.class), eq(1))).thenReturn(List.of(v));

    Vocabulary result = dao.findByAppId("appid-001");

    assertEquals(v, result);
  }

  @Test
  void findByAppIdReturnsNullWhenNotFound() {
    when(session.loadAll(eq(Vocabulary.class), any(Filter.class), eq(1))).thenReturn(List.of());

    Vocabulary result = dao.findByAppId("nonexistent");

    assertNull(result);
  }

  @Test
  void findByAppIdReturnsNullForBlankInput() {
    Vocabulary result = dao.findByAppId("  ");
    assertNull(result);
  }

  // ─── listAll ─────────────────────────────────────────────────────────────

  @Test
  void listAllReturnsSortedByLabelCaseInsensitive() {
    Vocabulary z = makeVocab("a1", "SKOS", true);
    Vocabulary a = makeVocab("a2", "datacite", true);
    Vocabulary m = makeVocab("a3", "PROV-O", true);
    when(session.loadAll(eq(Vocabulary.class), eq(1))).thenReturn(List.of(z, a, m));

    List<Vocabulary> result = dao.listAll();

    assertEquals(3, result.size());
    assertEquals("datacite", result.get(0).getLabel());
    assertEquals("PROV-O",   result.get(1).getLabel());
    assertEquals("SKOS",     result.get(2).getLabel());
  }

  // ─── listEnabled ─────────────────────────────────────────────────────────

  @Test
  void listEnabledFiltersOutDisabledVocabularies() {
    Vocabulary enabled  = makeVocab("e1", "Dublin Core", true);
    Vocabulary disabled = makeVocab("d1", "Material OWL", false);
    when(session.loadAll(eq(Vocabulary.class), eq(1))).thenReturn(List.of(enabled, disabled));

    List<Vocabulary> result = dao.listEnabled();

    assertEquals(1, result.size());
    assertEquals("e1", result.get(0).getAppId());
  }

  @Test
  void listEnabledReturnsEmptyWhenAllDisabled() {
    Vocabulary d1 = makeVocab("d1", "A", false);
    Vocabulary d2 = makeVocab("d2", "B", false);
    when(session.loadAll(eq(Vocabulary.class), eq(1))).thenReturn(List.of(d1, d2));

    List<Vocabulary> result = dao.listEnabled();

    assertTrue(result.isEmpty());
  }

  // ─── findVocabulariesUsedByEntity (TOOLS-CONTEXT-VOCAB-BACKEND-1) ────────

  @Test
  void findVocabulariesUsedByEntityReturnsEmptyForBlankAppId() {
    assertTrue(dao.findVocabulariesUsedByEntity("  ", "collection").isEmpty());
    assertTrue(dao.findVocabulariesUsedByEntity(null, "collection").isEmpty());
  }
}
