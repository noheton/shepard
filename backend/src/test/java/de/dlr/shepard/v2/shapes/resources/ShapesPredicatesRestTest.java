package de.dlr.shepard.v2.shapes.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.shapes.io.PredicateVocabularyEntryIO;
import de.dlr.shepard.v2.shapes.repositories.PredicateVocabularyRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ShapesPredicatesRest} — {@code GET /v2/shapes/predicates}.
 *
 * <p>Verifies:
 * <ol>
 *   <li>No substrate filter → {@link PredicateVocabularyRepository#count()} +
 *       {@link PredicateVocabularyRepository#findAll(int, int)} called</li>
 *   <li>Substrate filter → {@link PredicateVocabularyRepository#countBySubstrate(String)} +
 *       {@link PredicateVocabularyRepository#findBySubstrate(String, int, int)} called</li>
 *   <li>Blank substrate filter treated as absent (calls count/findAll)</li>
 *   <li>Empty result set → 200 with empty items list</li>
 *   <li>Endpoint is @RolesAllowed("authenticated")</li>
 *   <li>limit param caps the returned items list (DB-side slicing)</li>
 *   <li>PagedResponseIO envelope is returned with correct total</li>
 * </ol>
 */
class ShapesPredicatesRestTest {

  @Mock
  PredicateVocabularyRepository repository;

  @InjectMocks
  ShapesPredicatesRest rest;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ─── role gate ─────────────────────────────────────────────────────────────

  @Test
  void predicatesEndpoint_isAnnotatedWithAuthenticatedRole() throws Exception {
    var method = ShapesPredicatesRest.class.getMethod("predicates", String.class, int.class, int.class);
    RolesAllowed annotation = method.getAnnotation(RolesAllowed.class);
    assertNotNull(annotation, "GET /v2/shapes/predicates must be @RolesAllowed-gated");
    assertEquals("authenticated", annotation.value()[0]);
  }

  // ─── no filter ─────────────────────────────────────────────────────────────

  @Test
  void noSubstrateFilter_callsFindAll() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.count()).thenReturn(1L);
    when(repository.findAll(0, 200)).thenReturn(List.of(entry));

    Response r = rest.predicates(null, 200, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("neo4j", body.items().get(0).substrate());
    verify(repository).count();
    verify(repository).findAll(0, 200);
  }

  @Test
  void blankSubstrateFilter_callsFindAll() {
    when(repository.count()).thenReturn(1L);
    when(repository.findAll(0, 200)).thenReturn(List.of());

    Response r = rest.predicates("  ", 200, 0);

    assertEquals(200, r.getStatus());
    verify(repository).count();
    verify(repository).findAll(0, 200);
  }

  // ─── substrate filter ──────────────────────────────────────────────────────

  @Test
  void substrateFilter_neo4j_callsFindBySubstrate() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.countBySubstrate("neo4j")).thenReturn(1L);
    when(repository.findBySubstrate("neo4j", 0, 200)).thenReturn(List.of(entry));

    Response r = rest.predicates("neo4j", 200, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("neo4j", body.items().get(0).substrate());
    verify(repository).countBySubstrate("neo4j");
    verify(repository).findBySubstrate("neo4j", 0, 200);
  }

  @Test
  void substrateFilter_garage_callsFindBySubstrate() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#approvalDocument", "garage");
    when(repository.countBySubstrate("garage")).thenReturn(1L);
    when(repository.findBySubstrate("garage", 0, 200)).thenReturn(List.of(entry));

    Response r = rest.predicates("garage", 200, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("garage", body.items().get(0).substrate());
    verify(repository).countBySubstrate("garage");
    verify(repository).findBySubstrate("garage", 0, 200);
  }

  // ─── empty result ──────────────────────────────────────────────────────────

  @Test
  void emptyRepository_returns200WithEmptyItemsList() {
    when(repository.count()).thenReturn(0L);

    Response r = rest.predicates(null, 200, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
  }

  // ─── pageSize capping (DB-side slicing) ───────────────────────────────────

  @Test
  void pageSizeParam_capsReturnedItems() {
    List<PredicateVocabularyEntryIO> all = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      all.add(makeEntry("http://example.org/p" + i, "neo4j"));
    }
    when(repository.count()).thenReturn(10L);
    when(repository.findAll(0, 3)).thenReturn(all.subList(0, 3));

    Response r = rest.predicates(null, 3, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(3, body.items().size());
    assertEquals(10L, body.total());
    assertEquals(3, body.pageSize());
    assertEquals(0, body.page());
  }

  @Test
  void pageSizeGreaterThanTotal_returnsAllItems() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.count()).thenReturn(1L);
    when(repository.findAll(0, 500)).thenReturn(List.of(entry));

    Response r = rest.predicates(null, 500, 0);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
  }

  @Test
  void pageParam_returnsCorrectSlice() {
    List<PredicateVocabularyEntryIO> all = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      all.add(makeEntry("http://example.org/p" + i, "neo4j"));
    }
    when(repository.count()).thenReturn(10L);
    // page=1, pageSize=3 → skip=3, limit=3 → items[3..5]
    when(repository.findAll(3, 3)).thenReturn(all.subList(3, 6));

    Response r = rest.predicates(null, 3, 1);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(3, body.items().size());
    assertEquals(10L, body.total());
    assertEquals(3, body.pageSize());
    assertEquals(1, body.page());
    // page 1 of pageSize 3 → items[3..5]
    assertEquals("http://example.org/p3", body.items().get(0).predicateUri());
    assertEquals("http://example.org/p5", body.items().get(2).predicateUri());
  }

  @Test
  void pageParam_beyondTotal_returnsEmptyItems() {
    // skip = 5 * 200 = 1000 > total(1) → short-circuit, no findAll call
    when(repository.count()).thenReturn(1L);

    Response r = rest.predicates(null, 200, 5);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertTrue(body.items().isEmpty());
    assertEquals(1L, body.total());
  }

  // ─── helper ────────────────────────────────────────────────────────────────

  private PredicateVocabularyEntryIO makeEntry(String uri, String substrate) {
    return new PredicateVocabularyEntryIO(
      uri, substrate, "one", true,
      "Test description", "test.ttl", "2026-05-26T00:00:00Z"
    );
  }
}
