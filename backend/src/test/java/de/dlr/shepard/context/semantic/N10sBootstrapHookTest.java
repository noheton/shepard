package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Unit tests for {@link N10sBootstrapHook}. The three documented
 * branches (n10s absent, n10s present + fresh, n10s present +
 * already-initialised) are each exercised against a mocked OGM
 * {@link Session}. No Quarkus context.
 *
 * <p>Helper Result mocks are constructed <i>before</i> the
 * {@code when(session.query(...)).thenReturn(...)} stubbing to keep
 * Mockito's nested-stubbing detector happy.
 */
class N10sBootstrapHookTest {

  /** Branch 1: n10s absent — log warning, skip ensure + set + constraint. */
  @Test
  void run_skipsGraphConfigWhenN10sAbsent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.FALSE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, "IGNORE");
    hook.run();

    verify(session).query(eq(N10sBootstrapHook.DETECT_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.SET_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /** Branch 2a: n10s present, first run — ensure + set + constraint all fire. */
  @Test
  void run_ensuresGraphConfigAndConstraintWhenN10sPresent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, "IGNORE");
    hook.run();

    verify(session).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), eq(Map.of("handleVocabUris", "IGNORE")));
    verify(session).query(eq(N10sBootstrapHook.SET_CYPHER), eq(Map.of("handleVocabUris", "IGNORE")));
    verify(session).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /**
   * RESEED-FIND-N10S-SPARQL regression: ENSURE_GRAPHCONFIG_CYPHER is a plain MERGE
   * so it succeeds even when the graph has existing non-RDF nodes (Shepard migrations
   * create entity nodes before N10sBootstrapHook runs). The old n10s.graphconfig.init()
   * would have thrown here; the MERGE does not.
   */
  @Test
  void run_ensuresGraphConfigOnNonEmptyGraph() {
    // Simulate a session where the MERGE succeeds (no exception — the graph has
    // Shepard entity nodes but MERGE ignores them). Verify that both ENSURE and SET
    // are called with the correct handleVocabUris param.
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, "IGNORE");
    assertDoesNotThrow(hook::run);

    verify(session).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), eq(Map.of("handleVocabUris", "IGNORE")));
    verify(session).query(eq(N10sBootstrapHook.SET_CYPHER), eq(Map.of("handleVocabUris", "IGNORE")));
  }

  /**
   * Branch 2b: ensure fails (e.g. permissions or n10s internal error) → SET still
   * attempted, constraint still fires — fail-soft preserved.
   */
  @Test
  void run_swallowsEnsureGraphConfigFailure() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any()))
      .thenThrow(new RuntimeException("write denied"));

    var hook = new N10sBootstrapHook(session, "IGNORE");
    assertDoesNotThrow(hook::run);

    verify(session).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any());
    // SET is still attempted even if ENSURE failed
    verify(session).query(eq(N10sBootstrapHook.SET_CYPHER), any());
    // Constraint is also still attempted
    verify(session).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /** SET failure is swallowed — the MERGE-created _GraphConfig still serves a usable config. */
  @Test
  void run_swallowsSetFailure() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.SET_CYPHER), any()))
      .thenThrow(new RuntimeException("n10s set failed"));

    var hook = new N10sBootstrapHook(session, "IGNORE");
    assertDoesNotThrow(hook::run);

    verify(session).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any());
    verify(session).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /** Constraint failure is also swallowed — n10s lazily creates it on first import. */
  @Test
  void run_swallowsConstraintFailure() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any()))
      .thenThrow(new RuntimeException("forbidden"));

    var hook = new N10sBootstrapHook(session, "IGNORE");
    assertDoesNotThrow(hook::run);
  }

  /** Null session: warn + skip (no NPE). */
  @Test
  void run_handlesNullSession() {
    var hook = new N10sBootstrapHook(null, "IGNORE");
    assertDoesNotThrow(hook::run);
  }

  /** Detection probe raising → treated as "absent" — ensure + set never fire. */
  @Test
  void run_treatsDetectionExceptionAsAbsent() {
    Session session = mock(Session.class);
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenThrow(new RuntimeException("denied"));

    var hook = new N10sBootstrapHook(session, "IGNORE");
    hook.run();

    verify(session, never()).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.SET_CYPHER), any());
  }

  /** Detection probe returning empty result → treated as absent. */
  @Test
  void run_treatsEmptyDetectionAsAbsent() {
    Session session = mock(Session.class);
    Result empty = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(empty);

    var hook = new N10sBootstrapHook(session, "IGNORE");
    hook.run();

    verify(session, never()).query(eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER), any());
  }

  /** Operator-overridden handle-vocab-uris is forwarded to both ensure and set. */
  @Test
  void run_forwardsOperatorOverriddenHandleVocabUris() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, "SHORTEN");
    hook.run();

    verify(session, times(1)).query(
      eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER),
      eq(Map.of("handleVocabUris", "SHORTEN"))
    );
    verify(session, times(1)).query(
      eq(N10sBootstrapHook.SET_CYPHER),
      eq(Map.of("handleVocabUris", "SHORTEN"))
    );
  }

  /** Blank handle-vocab-uris falls back to the documented default. */
  @Test
  void run_fallsBackToDefaultHandleVocabUris() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, "   ");
    hook.run();

    verify(session).query(
      eq(N10sBootstrapHook.ENSURE_GRAPHCONFIG_CYPHER),
      eq(Map.of("handleVocabUris", N10sBootstrapHook.DEFAULT_HANDLE_VOCAB_URIS))
    );
    verify(session).query(
      eq(N10sBootstrapHook.SET_CYPHER),
      eq(Map.of("handleVocabUris", N10sBootstrapHook.DEFAULT_HANDLE_VOCAB_URIS))
    );
  }

  /** N1b: after a successful ensure+set+constraint, the seed service is invoked exactly once. */
  @Test
  void run_invokesOntologySeedServiceAfterBootstrap() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    OntologySeedService seed = mock(OntologySeedService.class);
    var hook = new N10sBootstrapHook(session, "IGNORE", seed);
    hook.run();

    verify(seed, times(1)).seedIfNeeded();
  }

  /** N1b: a seed service that raises is logged + swallowed; hook itself never throws. */
  @Test
  void run_swallowsOntologySeedException() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    OntologySeedService seed = mock(OntologySeedService.class);
    doThrow(new RuntimeException("seed exploded")).when(seed).seedIfNeeded();
    var hook = new N10sBootstrapHook(session, "IGNORE", seed);

    assertDoesNotThrow(hook::run);
    verify(seed, times(1)).seedIfNeeded();
  }

  /** N1b: n10s absent → bootstrap short-circuits BEFORE seeding. */
  @Test
  void run_doesNotInvokeSeedWhenN10sAbsent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.FALSE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    OntologySeedService seed = mock(OntologySeedService.class);
    var hook = new N10sBootstrapHook(session, "IGNORE", seed);
    hook.run();

    verify(seed, never()).seedIfNeeded();
  }

  // ----- helpers ----------------------------------------------------------

  static Result singleRow(Map<String, Object> row) {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(List.of(row));
    return result;
  }

  static Result emptyResult() {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(Collections.emptyList());
    return result;
  }
}
