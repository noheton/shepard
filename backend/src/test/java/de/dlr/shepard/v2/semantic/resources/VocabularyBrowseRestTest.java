package de.dlr.shepard.v2.semantic.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.v2.semantic.io.PredicateIO;
import de.dlr.shepard.v2.vocabularies.io.VocabularyIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-UI-FOLLOWUP — unit tests for {@link VocabularyBrowseRest}.
 *
 * <p>Covers the list-vocabularies happy path, the list-predicates happy path,
 * a 404 on a missing vocabulary, and the empty-predicates fallback.
 */
class VocabularyBrowseRestTest {

  private VocabularyDAO vocabularyDAO;
  private PredicateDAO predicateDAO;
  private VocabularyBrowseRest rest;

  @BeforeEach
  void setUp() {
    vocabularyDAO = mock(VocabularyDAO.class);
    predicateDAO  = mock(PredicateDAO.class);

    rest = new VocabularyBrowseRest();
    rest.vocabularyDAO = vocabularyDAO;
    rest.predicateDAO  = predicateDAO;
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static Vocabulary vocab(String appId, String uri, String label, boolean enabled) {
    Vocabulary v = new Vocabulary();
    v.setAppId(appId);
    v.setUri(uri);
    v.setLabel(label);
    v.setEnabled(enabled);
    return v;
  }

  private static Predicate predicate(String appId, String uri, String label, String vocabAppId, boolean required) {
    Predicate p = new Predicate();
    p.setAppId(appId);
    p.setUri(uri);
    p.setLabel(label);
    p.setVocabularyAppId(vocabAppId);
    p.setExpectedObjectType(Predicate.ExpectedObjectType.LITERAL.name());
    p.setCardinality(Predicate.Cardinality.MANY.name());
    p.setRequired(required);
    return p;
  }

  // ─── listVocabularies (APISIMP-VOCAB-LIST-UNBOUNDED) ─────────────────────

  @Test
  void listVocabulariesReturnsEmptyWhenNoneSeeded() {
    when(vocabularyDAO.count()).thenReturn(0L);
    when(vocabularyDAO.listPaged(0L, 50)).thenReturn(List.of());

    Response response = rest.listVocabularies(0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> out = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertNotNull(out);
    assertTrue(out.items().isEmpty());
    assertEquals(0L, out.total());
    assertEquals(0L, Long.parseLong(response.getHeaderString("X-Total-Count")));
  }

  @Test
  void listVocabulariesReturnsAllSeededRowsIncludingDisabled() {
    when(vocabularyDAO.count()).thenReturn(2L);
    when(vocabularyDAO.listPaged(0L, 50)).thenReturn(List.of(
      vocab("v-dcterms", "http://purl.org/dc/terms/", "Dublin Core Terms", true),
      vocab("v-disabled", "http://example.com/disabled#", "Disabled vocab", false)
    ));

    Response response = rest.listVocabularies(0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> out = (PagedResponseIO<VocabularyIO>) response.getEntity();
    assertEquals(2L, out.total());
    assertEquals(2, out.items().size());
    assertTrue(out.items().stream().anyMatch(v -> "v-dcterms".equals(v.getAppId()) && v.isEnabled()));
    assertTrue(out.items().stream().anyMatch(v -> "v-disabled".equals(v.getAppId()) && !v.isEnabled()));
  }

  @Test
  void listVocabulariesPaginatesCorrectly() {
    when(vocabularyDAO.count()).thenReturn(3L);
    when(vocabularyDAO.listPaged(0L, 2)).thenReturn(List.of(
      vocab("v-a", "http://example/a#", "Alpha", true),
      vocab("v-b", "http://example/b#", "Beta",  true)
    ));
    when(vocabularyDAO.listPaged(2L, 2)).thenReturn(List.of(
      vocab("v-c", "http://example/c#", "Gamma", false)
    ));

    Response page0 = rest.listVocabularies(0, 2);
    assertEquals(200, page0.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body0 = (PagedResponseIO<VocabularyIO>) page0.getEntity();
    assertEquals(3L, body0.total());
    assertEquals(2, body0.items().size());
    assertEquals("v-a", body0.items().get(0).getAppId());

    Response page1 = rest.listVocabularies(1, 2);
    @SuppressWarnings("unchecked")
    PagedResponseIO<VocabularyIO> body1 = (PagedResponseIO<VocabularyIO>) page1.getEntity();
    assertEquals(3L, body1.total());
    assertEquals(1, body1.items().size());
    assertEquals("v-c", body1.items().get(0).getAppId());
  }

  @Test
  void listVocabulariesXTotalCountHeader() {
    when(vocabularyDAO.count()).thenReturn(7L);
    when(vocabularyDAO.listPaged(0L, 50)).thenReturn(List.of());

    Response response = rest.listVocabularies(0, 50);

    assertEquals(7L, Long.parseLong(response.getHeaderString("X-Total-Count")));
  }

  // ─── listPredicatesForVocabulary ─────────────────────────────────────────

  @Test
  void listPredicatesReturns404WhenVocabularyMissing() {
    when(vocabularyDAO.findByAppId("missing-vocab-id")).thenReturn(null);

    Response response = rest.listPredicatesForVocabulary("missing-vocab-id", 0, 50);

    assertEquals(404, response.getStatus());
    verify(predicateDAO, never()).countByVocabulary(any());
    verify(predicateDAO, never()).listByVocabularyPaged(any(), anyLong(), anyInt());
  }

  @Test
  void listPredicatesReturns404WhenVocabIdIsBlank() {
    Response response = rest.listPredicatesForVocabulary("   ", 0, 50);

    assertEquals(404, response.getStatus());
    verify(vocabularyDAO, never()).findByAppId(any());
    verify(predicateDAO, never()).countByVocabulary(any());
    verify(predicateDAO, never()).listByVocabularyPaged(any(), anyLong(), anyInt());
  }

  @Test
  void listPredicatesReturns404WhenVocabIdIsNull() {
    Response response = rest.listPredicatesForVocabulary(null, 0, 50);

    assertEquals(404, response.getStatus());
    verify(predicateDAO, never()).countByVocabulary(any());
    verify(predicateDAO, never()).listByVocabularyPaged(any(), anyLong(), anyInt());
  }

  @Test
  void listPredicatesReturns200WithEmptyListWhenVocabularyHasNoPredicates() {
    String vid = "v-empty";
    when(vocabularyDAO.findByAppId(vid)).thenReturn(vocab(vid, "http://example/", "Empty", true));
    when(predicateDAO.countByVocabulary(vid)).thenReturn(0L);
    when(predicateDAO.listByVocabularyPaged(vid, 0L, 50)).thenReturn(List.of());

    Response response = rest.listPredicatesForVocabulary(vid, 0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateIO> body = (PagedResponseIO<PredicateIO>) response.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
  }

  @Test
  void listPredicatesReturns200WithPredicatesWhenPresent() {
    String vid = "v-dcterms";
    when(vocabularyDAO.findByAppId(vid)).thenReturn(vocab(vid, "http://purl.org/dc/terms/", "Dublin Core Terms", true));
    when(predicateDAO.countByVocabulary(vid)).thenReturn(2L);
    when(predicateDAO.listByVocabularyPaged(vid, 0L, 50)).thenReturn(List.of(
      predicate("p-creator", "http://purl.org/dc/terms/creator", "Creator", vid, true),
      predicate("p-title",   "http://purl.org/dc/terms/title",   "Title",   vid, false)
    ));

    Response response = rest.listPredicatesForVocabulary(vid, 0, 50);

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateIO> body = (PagedResponseIO<PredicateIO>) response.getEntity();
    assertEquals(2, body.items().size());
    assertEquals(2L, body.total());

    PredicateIO first = body.items().get(0);
    assertEquals("p-creator", first.appId());
    assertEquals("http://purl.org/dc/terms/creator", first.uri());
    assertEquals("Creator", first.label());
    assertEquals(vid, first.vocabularyAppId());
    assertEquals("LITERAL", first.expectedObjectType());
    assertEquals("MANY", first.cardinality());
    assertTrue(first.required());

    PredicateIO second = body.items().get(1);
    assertEquals("p-title", second.appId());
    assertEquals(false, second.required());
  }

  @Test
  void listPredicatesPaginatesCorrectly() {
    String vid = "v-big";
    when(vocabularyDAO.findByAppId(vid)).thenReturn(vocab(vid, "http://example/big#", "Big", true));
    List<Predicate> allPreds = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      allPreds.add(predicate("p-" + i, "http://example/p" + i, "P" + i, vid, false));
    }
    // page 0, pageSize 2 → skip=0
    when(predicateDAO.countByVocabulary(vid)).thenReturn(5L);
    when(predicateDAO.listByVocabularyPaged(vid, 0L, 2)).thenReturn(allPreds.subList(0, 2));
    // page 2, pageSize 2 → skip=4
    when(predicateDAO.listByVocabularyPaged(vid, 4L, 2)).thenReturn(allPreds.subList(4, 5));

    Response page0 = rest.listPredicatesForVocabulary(vid, 0, 2);
    assertEquals(200, page0.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateIO> body0 = (PagedResponseIO<PredicateIO>) page0.getEntity();
    assertEquals(5L, body0.total());
    assertEquals(2, body0.items().size());
    assertEquals("p-0", body0.items().get(0).appId());

    Response page2 = rest.listPredicatesForVocabulary(vid, 2, 2);
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateIO> body2 = (PagedResponseIO<PredicateIO>) page2.getEntity();
    assertEquals(5L, body2.total());
    assertEquals(1, body2.items().size()); // only p-4 left
    assertEquals("p-4", body2.items().get(0).appId());
  }

  // ─── listVocabulariesUsedBy (TOOLS-CONTEXT-VOCAB-BACKEND-1) ──────────────

  @Test
  void listVocabulariesUsedByReturnsEmptyWhenAppIdBlank() {
    Response response = rest.listVocabulariesUsedBy("  ", "collection");
    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    List<VocabularyIO> out = (List<VocabularyIO>) response.getEntity();
    assertTrue(out.isEmpty());
    verify(vocabularyDAO, never()).findVocabulariesUsedByEntity("  ", "collection");
  }

  @Test
  void listVocabulariesUsedByReturnsEmptyWhenAppIdNull() {
    Response response = rest.listVocabulariesUsedBy(null, "data-object");
    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    List<VocabularyIO> out = (List<VocabularyIO>) response.getEntity();
    assertTrue(out.isEmpty());
  }

  @Test
  void listVocabulariesUsedByForwardsScopeToDao() {
    String appId = "c-123";
    when(vocabularyDAO.findVocabulariesUsedByEntity(appId, "collection")).thenReturn(List.of(
      vocab("v-dcterms", "http://purl.org/dc/terms/", "Dublin Core Terms", true)
    ));

    Response response = rest.listVocabulariesUsedBy(appId, "collection");

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    List<VocabularyIO> out = (List<VocabularyIO>) response.getEntity();
    assertEquals(1, out.size());
    assertEquals("v-dcterms", out.get(0).getAppId());
    verify(vocabularyDAO).findVocabulariesUsedByEntity(appId, "collection");
  }

  @Test
  void listVocabulariesUsedByReturnsEmptyWhenDaoReturnsEmpty() {
    String appId = "d-no-annotations";
    when(vocabularyDAO.findVocabulariesUsedByEntity(appId, "data-object")).thenReturn(List.of());

    Response response = rest.listVocabulariesUsedBy(appId, "data-object");

    assertEquals(200, response.getStatus());
    @SuppressWarnings("unchecked")
    List<VocabularyIO> out = (List<VocabularyIO>) response.getEntity();
    assertTrue(out.isEmpty());
  }

  // ─── APISIMP-VOCAB-BROWSE-SCOPE-UNDOCUMENTED: @Parameter regression ───────

  @Test
  void listVocabulariesUsedBy_scopeParamIsDocumented() throws NoSuchMethodException {
    java.lang.reflect.Method method = VocabularyBrowseRest.class.getMethod(
        "listVocabulariesUsedBy", String.class, String.class);
    java.lang.reflect.Parameter param = Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "scope".equals(qp.value());
        })
        .findFirst()
        .orElse(null);
    assertNotNull(param, "scope must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "scope must carry @Parameter annotation (APISIMP-VOCAB-BROWSE-SCOPE-UNDOCUMENTED)");
    assertTrue(
        ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for scope");
  }
}
