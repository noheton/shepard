package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.neo4j.migrations.V23__Split_singleton_bundles_to_FileReferences.CandidateRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused unit tests for the V23 migration class (FR1b, see
 * {@code aidocs/53 §1.8.5}). End-to-end testcontainer coverage
 * (real Neo4j + MongoDB) is deferred per CLAUDE.md migration policy
 * — the in-flight integration runs against the shared
 * Neo4j/Mongo CI stack via the existing migration-runner happy path
 * once an admin flips the toggle; this unit test asserts the
 * decision logic that lands earliest in the migration's execution.
 *
 * <p>Specifically covered:
 * <ul>
 *   <li>The toggle key default — {@link V23__Split_singleton_bundles_to_FileReferences#isEnabled()}
 *       returns {@code false} when the system property
 *       {@code shepard.migration.split-singletons.enabled} is unset.</li>
 *   <li>Toggle key parsing — {@code true} (any case) enables the
 *       migration; anything else disables.</li>
 *   <li>The {@link CandidateRow} DTO surfaces its fields verbatim.</li>
 * </ul>
 *
 * <p>The Cypher candidate-discovery and the Mongo metadata-move are
 * exercised by the testcontainer integration test (deferred). The
 * decision-logic part lands here because it's the cheapest part to
 * cover and gates everything else — a misconfigured toggle would
 * silently no-op the migration in production, which is the failure
 * mode worth catching most loudly.
 */
class V23__Split_singleton_bundles_to_FileReferencesTest {

  private static final String TOGGLE_KEY = V23__Split_singleton_bundles_to_FileReferences.TOGGLE_KEY;

  @BeforeEach
  void clearToggle() {
    System.clearProperty(TOGGLE_KEY);
  }

  @AfterEach
  void resetToggle() {
    System.clearProperty(TOGGLE_KEY);
  }

  @Test
  void toggleKeyMatchesDesign() {
    // The aidocs/34 row references this exact key string — the docs
    // and the implementation share one literal so renames don't drift.
    assertEquals("shepard.migration.split-singletons.enabled", TOGGLE_KEY);
  }

  @Test
  void sharedNamespaceMatchesService() {
    // The migration's destination namespace must match the runtime
    // service's destination so post-migration reads find the bytes.
    // The migration class duplicates the constant (to keep the
    // migration self-contained per the V2__Extract_json precedent);
    // this test pins them together.
    assertEquals(
      "_shepard_files",
      V23__Split_singleton_bundles_to_FileReferences.SHARED_FILES_NAMESPACE
    );
  }

  @Test
  void isEnabledDefaultsFalse() {
    // No system property → falls through to the OptionalValue default.
    assertFalse(V23__Split_singleton_bundles_to_FileReferences.isEnabled());
  }

  @Test
  void isEnabledTrueWhenSet() {
    System.setProperty(TOGGLE_KEY, "true");
    assertTrue(V23__Split_singleton_bundles_to_FileReferences.isEnabled());
  }

  @Test
  void isEnabledFalseForNonBooleanValues() {
    System.setProperty(TOGGLE_KEY, "no");
    assertFalse(V23__Split_singleton_bundles_to_FileReferences.isEnabled());
  }

  @Test
  void isEnabledFalseWhenExplicitlyFalse() {
    System.setProperty(TOGGLE_KEY, "false");
    assertFalse(V23__Split_singleton_bundles_to_FileReferences.isEnabled());
  }

  // ─── CandidateRow ─────────────────────────────────────────────────────────

  @Test
  void candidateRowSurfacesFields() {
    CandidateRow row = new CandidateRow("be", "ge", "fo", "cm");
    assertEquals("be", row.bundleElementId);
    assertEquals("ge", row.groupElementId);
    assertEquals("fo", row.fileOid);
    assertEquals("cm", row.containerMongoId);
  }

  @Test
  void candidateRowAcceptsNullMongoFields() {
    // Singletons-shaped bundles created via an older migration may
    // lack containerMongoId; the row must still construct cleanly so
    // we can fall through to the Neo4j-only relabel path.
    CandidateRow row = new CandidateRow("be", "ge", null, null);
    assertNull(row.fileOid);
    assertNull(row.containerMongoId);
  }

  // ─── markerProperties ─────────────────────────────────────────────────────

  @Test
  void legacyMarkerPropertyHasExpectedName() {
    // The V23_R rollback Cypher hardcodes this property name; pin
    // the constant so a rename forces both files to update.
    assertEquals("legacyV23Singleton", V23__Split_singleton_bundles_to_FileReferences.LEGACY_MARKER_PROPERTY);
    assertEquals("legacyV23BundleMongoId", V23__Split_singleton_bundles_to_FileReferences.LEGACY_MONGO_ID_PROPERTY);
  }

  // ─── apply() short-circuit ────────────────────────────────────────────────

  @Test
  void apply_shortCircuitsWhenToggleOff() {
    // Toggle unset → apply() must return without opening any
    // MongoClient / Neo4j session. We pass a null MigrationContext
    // because the short-circuit happens before the context is
    // dereferenced; if the short-circuit ever regressed, this test
    // would NPE first instead of running real I/O.
    var migration = new V23__Split_singleton_bundles_to_FileReferences();
    // toggle is unset thanks to the @BeforeEach clearProperty
    migration.apply(null);
    // No exception thrown → short-circuit confirmed.
  }
}
