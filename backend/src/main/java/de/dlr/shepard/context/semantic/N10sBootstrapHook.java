package de.dlr.shepard.context.semantic;

import io.quarkus.logging.Log;
import java.util.Collections;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.session.Session;

/**
 * N1a — startup hook that initialises the n10s graph configuration
 * for shepard's {@link SemanticRepositoryType#INTERNAL} repositories.
 *
 * <p>Runs from {@code ShepardMain.init} <b>after</b>
 * {@code MigrationsRunner.apply()} (i.e. after the A1e fail-fast
 * pre-A0 invariant) and after {@code NeoConnector.connect()}, so
 * the OGM session is available. Three branches:
 * <ol>
 *   <li><b>n10s absent.</b> {@code CALL dbms.procedures()} returns
 *       nothing under the {@code n10s.*} namespace → log a warning and
 *       skip silently. The instance simply doesn't expose
 *       {@code INTERNAL} repositories; everything else (external
 *       {@code SPARQL}/{@code JSKOS}/{@code SKOSMOS}) still works.</li>
 *   <li><b>n10s present, graphconfig already initialised.</b>
 *       {@code CALL n10s.graphconfig.init} is idempotent — it accepts
 *       a re-init and updates the config in place. We call it
 *       anyway to converge on the operator-configured
 *       {@code handle-vocab-uris} value if it changed.</li>
 *   <li><b>n10s present, first run.</b> Run
 *       {@code n10s.graphconfig.init} with shepard's defaults, then
 *       ensure the {@code n10s_unique_uri} constraint exists (the
 *       constraint also gets created by n10s itself on first
 *       import, but we keep the explicit
 *       {@code CREATE CONSTRAINT IF NOT EXISTS} for an admin
 *       running {@code cypher-shell} who'd like to see it
 *       pre-flighted).</li>
 * </ol>
 *
 * <p>The hook is fail-soft by design: any Cypher error during
 * bootstrap is logged at WARN and swallowed. The connector itself
 * reports {@code healthCheck() == false} downstream, which lets the
 * rest of shepard treat an n10s-less instance as
 * "{@code INTERNAL} repositories disabled" rather than "service
 * down". Hard failures here would block the whole shepard startup
 * for a feature that's strictly additive.
 */
public class N10sBootstrapHook {

  /** Config key — true (default) means try to run the bootstrap; false to fully disable. */
  static final String ENABLED_PROPERTY = "shepard.semantic.internal.enabled";

  /** Config key — passed through verbatim to n10s {@code graphconfig.init({handleVocabUris})}. */
  static final String HANDLE_VOCAB_URIS_PROPERTY = "shepard.semantic.internal.handle-vocab-uris";

  /** Default {@code handleVocabUris}; per aidocs/48 §3.2 we want "IGNORE" (no prefix-shortening on read). */
  static final String DEFAULT_HANDLE_VOCAB_URIS = "IGNORE";

  /** Cypher that detects whether n10s procedures are registered. */
  static final String DETECT_CYPHER =
    "CALL dbms.procedures() YIELD name WHERE name STARTS WITH 'n10s.' " +
    "RETURN count(name) > 0 AS available";

  /**
   * n10s graphconfig.init Cypher. {@code applyNeo4jNaming=true} keeps
   * IRIs distinct on round-trip even when {@code handleVocabUris} is
   * IGNORE; {@code keepLangTag=true} preserves language tags as
   * property-name suffixes ({@code rdfs__label@en}); {@code handleMultival=ARRAY}
   * means n10s persists per-language label arrays we then parse out
   * via {@link InternalSemanticConnector#extractLabels}.
   */
  static final String INIT_CYPHER =
    "CALL n10s.graphconfig.init({" +
    "handleVocabUris: $handleVocabUris, " +
    "applyNeo4jNaming: true, " +
    "keepLangTag: true, " +
    "handleMultival: 'ARRAY', " +
    "handleRDFTypes: 'LABELS_AND_NODES'" +
    "})";

  /**
   * Idempotent constraint on the n10s-managed {@code :Resource} label.
   * n10s itself creates this on first import; pre-creating it here
   * gives an admin running {@code cypher-shell SHOW CONSTRAINTS} a
   * predictable picture from start-of-day.
   */
  static final String CONSTRAINT_CYPHER =
    "CREATE CONSTRAINT n10s_unique_uri IF NOT EXISTS " +
    "FOR (r:Resource) REQUIRE r.uri IS UNIQUE";

  private final Session session;
  private final boolean enabled;
  private final String handleVocabUris;

  /** Production ctor — reads config + the OGM session at call time. */
  public N10sBootstrapHook() {
    this(
      de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession(),
      readBooleanConfig(ENABLED_PROPERTY, true),
      readStringConfig(HANDLE_VOCAB_URIS_PROPERTY, DEFAULT_HANDLE_VOCAB_URIS)
    );
  }

  /** Test seam — accept a pre-built session + the two config values. */
  public N10sBootstrapHook(Session session, boolean enabled, String handleVocabUris) {
    this.session = session;
    this.enabled = enabled;
    this.handleVocabUris = handleVocabUris == null || handleVocabUris.isBlank()
      ? DEFAULT_HANDLE_VOCAB_URIS
      : handleVocabUris.trim();
  }

  /**
   * Run the bootstrap. Idempotent — safe to call on every startup.
   * Never throws on n10s-related errors; logs and proceeds.
   */
  public void run() {
    if (!enabled) {
      Log.info("N10sBootstrapHook: disabled via shepard.semantic.internal.enabled=false; skipping.");
      return;
    }
    if (session == null) {
      Log.warn("N10sBootstrapHook: no OGM session available; skipping (INTERNAL repos disabled).");
      return;
    }

    if (!detectN10s()) {
      Log.warn(
        "N10sBootstrapHook: neosemantics (n10s) procedures not registered in Neo4j. " +
        "SemanticRepositoryType.INTERNAL will report unhealthy. " +
        "To enable, install the n10s plugin (NEO4J_PLUGINS=[\"n10s\"]) and " +
        "allow it in dbms.security.procedures.allowlist."
      );
      return;
    }

    try {
      session.query(INIT_CYPHER, Map.of("handleVocabUris", handleVocabUris));
    } catch (RuntimeException ex) {
      // n10s.graphconfig.init raises "Graph config exists" rather
      // than a typed "already initialised" — accept and continue.
      Log.warnf(
        "N10sBootstrapHook: graphconfig.init returned %s — treating as already-initialised.",
        ex.getClass().getSimpleName()
      );
    }

    try {
      session.query(CONSTRAINT_CYPHER, Collections.emptyMap());
    } catch (RuntimeException ex) {
      Log.warnf(
        "N10sBootstrapHook: failed to ensure n10s_unique_uri constraint (%s); n10s will create it on first import.",
        ex.getClass().getSimpleName()
      );
    }

    Log.info("N10sBootstrapHook: n10s INTERNAL semantic repository ready.");
  }

  private boolean detectN10s() {
    try {
      var result = session.query(DETECT_CYPHER, Collections.emptyMap());
      var it = result.queryResults().iterator();
      if (!it.hasNext()) return false;
      return Boolean.TRUE.equals(it.next().get("available"));
    } catch (RuntimeException ex) {
      Log.warnf("N10sBootstrapHook: detection probe raised %s; treating n10s as absent.", ex.getClass().getSimpleName());
      return false;
    }
  }

  private static boolean readBooleanConfig(String key, boolean fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, Boolean.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  private static String readStringConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }
}
