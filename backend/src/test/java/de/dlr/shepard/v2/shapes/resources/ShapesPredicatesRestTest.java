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
 *   <li>No substrate filter → {@link PredicateVocabularyRepository#findAll()} called</li>
 *   <li>Substrate filter → {@link PredicateVocabularyRepository#findBySubstrate(String)} called</li>
 *   <li>Blank substrate filter treated as absent (calls findAll)</li>
 *   <li>Empty result set → 200 with empty items list</li>
 *   <li>Endpoint is @RolesAllowed("authenticated")</li>
 *   <li>limit param caps the returned items list</li>
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
    var method = ShapesPredicatesRest.class.getMethod("predicates", String.class, int.class);
    RolesAllowed annotation = method.getAnnotation(RolesAllowed.class);
    assertNotNull(annotation, "GET /v2/shapes/predicates must be @RolesAllowed-gated");
    assertEquals("authenticated", annotation.value()[0]);
  }

  // ─── no filter ─────────────────────────────────────────────────────────────

  @Test
  void noSubstrateFilter_callsFindAll() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.findAll()).thenReturn(List.of(entry));

    Response r = rest.predicates(null, 200);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("neo4j", body.items().get(0).substrate());
    verify(repository).findAll();
  }

  @Test
  void blankSubstrateFilter_callsFindAll() {
    when(repository.findAll()).thenReturn(List.of());

    Response r = rest.predicates("  ", 200);

    assertEquals(200, r.getStatus());
    verify(repository).findAll();
  }

  // ─── substrate filter ──────────────────────────────────────────────────────

  @Test
  void substrateFilter_neo4j_callsFindBySubstrate() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.findBySubstrate("neo4j")).thenReturn(List.of(entry));

    Response r = rest.predicates("neo4j", 200);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("neo4j", body.items().get(0).substrate());
    verify(repository).findBySubstrate("neo4j");
  }

  @Test
  void substrateFilter_garage_callsFindBySubstrate() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#approvalDocument", "garage");
    when(repository.findBySubstrate("garage")).thenReturn(List.of(entry));

    Response r = rest.predicates("garage", 200);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals("garage", body.items().get(0).substrate());
    verify(repository).findBySubstrate("garage");
  }

  // ─── empty result ──────────────────────────────────────────────────────────

  @Test
  void emptyRepository_returns200WithEmptyItemsList() {
    when(repository.findAll()).thenReturn(List.of());

    Response r = rest.predicates(null, 200);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertNotNull(body);
    assertTrue(body.items().isEmpty());
    assertEquals(0L, body.total());
  }

  // ─── limit capping ─────────────────────────────────────────────────────────

  @Test
  void limitParam_capsReturnedItems() {
    List<PredicateVocabularyEntryIO> all = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      all.add(makeEntry("http://example.org/p" + i, "neo4j"));
    }
    when(repository.findAll()).thenReturn(all);

    Response r = rest.predicates(null, 3);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(3, body.items().size());
    assertEquals(10L, body.total());
    assertEquals(3, body.pageSize());
    assertEquals(0, body.page());
  }

  @Test
  void limitGreaterThanTotal_returnsAllItems() {
    var entry = makeEntry("http://semantics.dlr.de/shepard-upper#status", "neo4j");
    when(repository.findAll()).thenReturn(List.of(entry));

    Response r = rest.predicates(null, 500);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PredicateVocabularyEntryIO> body = (PagedResponseIO<PredicateVocabularyEntryIO>) r.getEntity();
    assertEquals(1, body.items().size());
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
