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

  /** Branch 1: n10s absent — log warning, skip both init + constraint. */
  @Test
  void run_skipsInitWhenN10sAbsent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.FALSE));
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    hook.run();

    verify(session).query(eq(N10sBootstrapHook.DETECT_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.INIT_CYPHER), any());
    verify(session, never()).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /** Branch 2a: n10s present, first run — both init + constraint fire. */
  @Test
  void run_invokesInitAndConstraintWhenN10sPresent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    hook.run();

    verify(session).query(eq(N10sBootstrapHook.INIT_CYPHER), eq(Map.of("handleVocabUris", "IGNORE")));
    verify(session).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /**
   * Branch 2b: n10s present + graphconfig already exists → init
   * raises and is swallowed; constraint still fires.
   */
  @Test
  void run_swallowsAlreadyInitialisedError() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenThrow(new RuntimeException("Graph config exists"));
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    assertDoesNotThrow(hook::run);

    verify(session).query(eq(N10sBootstrapHook.INIT_CYPHER), any());
    verify(session).query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any());
  }

  /** Constraint failure is also swallowed — n10s lazily creates it on first import. */
  @Test
  void run_swallowsConstraintFailure() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenThrow(new RuntimeException("forbidden"));

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    assertDoesNotThrow(hook::run);
  }

  /** Branch 3: disabled via config — nothing fires, even with a healthy session. */
  @Test
  void run_isShortCircuitedByDisabledConfig() {
    Session session = mock(Session.class);
    var hook = new N10sBootstrapHook(session, false, "IGNORE");
    hook.run();
    verify(session, never()).query(any(String.class), any());
  }

  /** Null session: warn + skip (no NPE). */
  @Test
  void run_handlesNullSession() {
    var hook = new N10sBootstrapHook(null, true, "IGNORE");
    assertDoesNotThrow(hook::run);
  }

  /** Detection probe raising → treated as "absent" — init never fires. */
  @Test
  void run_treatsDetectionExceptionAsAbsent() {
    Session session = mock(Session.class);
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenThrow(new RuntimeException("denied"));

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    hook.run();

    verify(session, never()).query(eq(N10sBootstrapHook.INIT_CYPHER), any());
  }

  /** Detection probe returning empty result → treated as absent. */
  @Test
  void run_treatsEmptyDetectionAsAbsent() {
    Session session = mock(Session.class);
    Result empty = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(empty);

    var hook = new N10sBootstrapHook(session, true, "IGNORE");
    hook.run();

    verify(session, never()).query(eq(N10sBootstrapHook.INIT_CYPHER), any());
  }

  /** Operator-overridden handle-vocab-uris is forwarded to graphconfig.init. */
  @Test
  void run_forwardsOperatorOverriddenHandleVocabUris() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    var hook = new N10sBootstrapHook(session, true, "SHORTEN");
    hook.run();

    verify(session, times(1)).query(eq(N10sBootstrapHook.INIT_CYPHER), eq(Map.of("handleVocabUris", "SHORTEN")));
  }

  /** Blank handle-vocab-uris falls back to the documented default. */
  @Test
  void run_fallsBackToDefaultHandleVocabUris() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    var hook = new N10sBootstrapHook(session, true, "   ");
    hook.run();

    verify(session).query(
      eq(N10sBootstrapHook.INIT_CYPHER),
      eq(Map.of("handleVocabUris", N10sBootstrapHook.DEFAULT_HANDLE_VOCAB_URIS))
    );
  }

  /** N1b: after a successful init+constraint pair, the seed service is invoked exactly once. */
  @Test
  void run_invokesOntologySeedServiceAfterBootstrap() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    OntologySeedService seed = mock(OntologySeedService.class);
    var hook = new N10sBootstrapHook(session, true, "IGNORE", seed);
    hook.run();

    verify(seed, times(1)).seedIfNeeded();
  }

  /** N1b: a seed service that raises is logged + swallowed; hook itself never throws. */
  @Test
  void run_swallowsOntologySeedException() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result initOk = emptyResult();
    Result constraintOk = emptyResult();
    when(session.query(eq(N10sBootstrapHook.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(N10sBootstrapHook.INIT_CYPHER), any())).thenReturn(initOk);
    when(session.query(eq(N10sBootstrapHook.CONSTRAINT_CYPHER), any())).thenReturn(constraintOk);

    OntologySeedService seed = mock(OntologySeedService.class);
    doThrow(new RuntimeException("seed exploded")).when(seed).seedIfNeeded();
    var hook = new N10sBootstrapHook(session, true, "IGNORE", seed);

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
    var hook = new N10sBootstrapHook(session, true, "IGNORE", seed);
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
