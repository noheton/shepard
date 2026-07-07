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
 *       A {@code MERGE (gc:_GraphConfig) ON CREATE SET ...} is a no-op;
 *       {@code CALL n10s.graphconfig.set(...)} then converges the
 *       operator-configured {@code handle-vocab-uris} value.</li>
 *   <li><b>n10s present, first run.</b> The MERGE creates
 *       {@code _GraphConfig} with shepard's defaults, then
 *       {@code graphconfig.set} applies the full param set, then
 *       the {@code n10s_unique_uri} constraint is ensured (n10s
 *       also creates it on first import, but pre-flighting it here
 *       gives an admin running {@code cypher-shell SHOW CONSTRAINTS}
 *       a predictable picture from day one).
 *       Using MERGE instead of {@code n10s.graphconfig.init} avoids
 *       the "graph must be empty" restriction that caused
 *       RESEED-FIND-N10S-SPARQL: on a fresh-wipe instance, V-series
 *       migrations create Shepard entity nodes before this hook runs,
 *       which caused {@code init} to throw and leave
 *       {@code _GraphConfig} uncreated.</li>
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

  /** Config key — passed through verbatim to n10s {@code graphconfig.set({handleVocabUris})}. */
  static final String HANDLE_VOCAB_URIS_PROPERTY = "shepard.semantic.internal.handle-vocab-uris";

  /** Default {@code handleVocabUris}; per aidocs/48 §3.2 we want "IGNORE" (no prefix-shortening on read). */
  static final String DEFAULT_HANDLE_VOCAB_URIS = "IGNORE";

  // dbms.procedures() was removed in Neo4j 5.26; SHOW PROCEDURES is the
  // supported alternative in the 5.x series.
  static final String DETECT_CYPHER =
    "SHOW PROCEDURES YIELD name WHERE name STARTS WITH 'n10s.' " +
    "RETURN count(name) > 0 AS available";

  /**
   * MERGE the {@code _GraphConfig} singleton that n10s uses to store its
   * graph-wide configuration. Uses a plain Cypher MERGE rather than
   * {@code CALL n10s.graphconfig.init()} because {@code init} requires a
   * completely empty graph and fails on fresh-wipe instances where Shepard's
   * own V-series migrations have already created entity nodes
   * (RESEED-FIND-N10S-SPARQL).
   *
   * <p>The MERGE only sets properties ON CREATE, so existing configs are
   * not overwritten here. Mutable params are applied by {@link #SET_CYPHER}
   * immediately after.
   */
  static final String ENSURE_GRAPHCONFIG_CYPHER =
    "MERGE (gc:_GraphConfig) " +
    "ON CREATE SET " +
    "  gc.handleVocabUris = $handleVocabUris, " +
    "  gc.handleRDFTypes = 'LABELS_AND_NODES', " +
    "  gc.handleMultival = 'ARRAY', " +
    "  gc.handleCustomDataTypes = 'IGNORE', " +
    "  gc.keepLangTag = true, " +
    "  gc.keepCustomDataTypes = false, " +
    "  gc.applyNeo4jNaming = true";

  /**
   * Apply the operator-configured params via the n10s-managed procedure.
   * Called after {@link #ENSURE_GRAPHCONFIG_CYPHER} ensures {@code _GraphConfig}
   * exists. {@code applyNeo4jNaming=true} keeps IRIs distinct on round-trip
   * even when {@code handleVocabUris} is IGNORE; {@code keepLangTag=true}
   * preserves language tags as property-name suffixes ({@code rdfs__label@en});
   * {@code handleMultival=ARRAY} means n10s persists per-language label
   * arrays we then parse via {@link InternalSemanticConnector#extractLabels}.
   */
  static final String SET_CYPHER =
    "CALL n10s.graphconfig.set({" +
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
  private final String handleVocabUris;
  private final OntologySeedService seedService;

  /** Production ctor — reads config + the OGM session at call time. */
  public N10sBootstrapHook() {
    this(
      de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession(),
      readStringConfig(HANDLE_VOCAB_URIS_PROPERTY, DEFAULT_HANDLE_VOCAB_URIS),
      new OntologySeedService()
    );
  }

  /**
   * Test seam (pre-N1b) — accept a pre-built session + handleVocabUris.
   * The seed service is constructed in disabled mode so pre-N1b tests get
   * the original semantics: bootstrap-only, no ontology import.
   */
  public N10sBootstrapHook(Session session, String handleVocabUris) {
    this(
      session,
      handleVocabUris,
      new OntologySeedService(
        session,
        false, // pre-seed disabled — preserves pre-N1b test contract
        java.util.Collections.<String>emptySet(),
        null,
        null
      )
    );
  }

  /** Test seam (N1b) — accept a pre-built session, handleVocabUris, and a pre-built seed service. */
  public N10sBootstrapHook(Session session, String handleVocabUris, OntologySeedService seedService) {
    this.session = session;
    this.handleVocabUris = handleVocabUris == null || handleVocabUris.isBlank()
      ? DEFAULT_HANDLE_VOCAB_URIS
      : handleVocabUris.trim();
    this.seedService = seedService;
  }

  /**
   * Run the bootstrap. Idempotent — safe to call on every startup.
   * Never throws on n10s-related errors; logs and proceeds.
   */
  public void run() {
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

    // Step 1: Ensure the n10s _GraphConfig singleton exists.
    // MERGE is idempotent and works regardless of graph state — unlike
    // n10s.graphconfig.init() which refuses to run on a non-empty graph
    // and failed on fresh-wipe instances after V-series migrations
    // created Shepard entity nodes (RESEED-FIND-N10S-SPARQL).
    try {
      session.query(ENSURE_GRAPHCONFIG_CYPHER, Map.of("handleVocabUris", handleVocabUris));
    } catch (RuntimeException ex) {
      Log.warnf(
        "N10sBootstrapHook: failed to ensure _GraphConfig (%s); n10s SPARQL may be unavailable.",
        ex.getClass().getSimpleName()
      );
    }

    // Step 2: Apply operator-configured params via the n10s procedure.
    // This converges the config when the operator changes handleVocabUris;
    // it is a no-op when values are already correct.
    try {
      session.query(SET_CYPHER, Map.of("handleVocabUris", handleVocabUris));
    } catch (RuntimeException ex) {
      Log.warnf(
        "N10sBootstrapHook: graphconfig.set returned %s — n10s config may not reflect current handleVocabUris.",
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

    Log.info("N10sBootstrapHook: n10s graph config ensured; INTERNAL semantic repository ready.");

    // N1b — pre-seed the common ontologies bundle (PROV-O / Dublin
    // Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL).
    // Fail-soft: any error per-bundle is logged + swallowed by the
    // seed service. The toggle
    // `shepard.semantic.internal.preseed-ontologies.enabled` (default
    // ON) lets operators opt out and run a bare n10s.
    if (seedService != null) {
      try {
        seedService.seedIfNeeded();
      } catch (RuntimeException ex) {
        // Defensive — the service is fail-soft internally; this is
        // belt-and-braces so a future regression can't take down
        // shepard startup.
        Log.warnf(
          "N10sBootstrapHook: ontology pre-seed raised %s (%s); continuing.",
          ex.getClass().getSimpleName(),
          ex.getMessage()
        );
      }
    }
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

  private static String readStringConfig(String key, String fallback) {
    try {
      return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(fallback);
    } catch (RuntimeException ex) {
      return fallback;
    }
  }
}
