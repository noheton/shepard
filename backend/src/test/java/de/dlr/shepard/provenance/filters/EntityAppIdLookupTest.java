package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityAppIdLookup} — focus on the static allow-list +
 * label-regex guard. The Cypher round-trip is covered implicitly by the live
 * e2e at {@code e2e/tests/rdm-004b-provenance-resolves-targets.spec.ts}; a
 * Neo4j-bound unit test here would require a testcontainer the rest of the
 * provenance suite does not pull in.
 */
class EntityAppIdLookupTest {

  EntityAppIdLookup lookup = new EntityAppIdLookup();

  @Test
  void disallowedLabelReturnsEmpty() {
    // No allow-list entry → no DAO call → empty (safe default).
    assertTrue(lookup.findAppIdByNumericId("Robert'); DROP TABLE Students;--", 1L).isEmpty());
    assertTrue(lookup.findAppIdByNumericId("UnknownLabel", 1L).isEmpty());
    assertTrue(lookup.findAppIdByNumericId(null, 1L).isEmpty());
  }

  @Test
  void allowListCoversEveryKindFromParser() {
    // Every value in PathTargetParser.PLURAL_TO_KIND must be in the lookup's
    // allow-list — otherwise numeric-id resolution will silently fail for
    // that kind. Detect drift early.
    for (String kind : PathTargetParser.PLURAL_TO_KIND.values()) {
      assertTrue(
        EntityAppIdLookup.ALLOWED_LABELS.contains(kind),
        "EntityAppIdLookup.ALLOWED_LABELS missing kind '" + kind + "' (referenced from PathTargetParser)"
      );
    }
  }

  @Test
  void labelRegexAcceptsValidNeo4jLabels() {
    for (String label : EntityAppIdLookup.ALLOWED_LABELS) {
      assertTrue(EntityAppIdLookup.LABEL_RE.matcher(label).matches(), "Label rejected: " + label);
    }
  }

  @Test
  void labelRegexRejectsInjectionAttempts() {
    assertFalse(EntityAppIdLookup.LABEL_RE.matcher("Collection`)").matches());
    assertFalse(EntityAppIdLookup.LABEL_RE.matcher("Collection MATCH").matches());
    assertFalse(EntityAppIdLookup.LABEL_RE.matcher("Collection;DROP").matches());
    assertFalse(EntityAppIdLookup.LABEL_RE.matcher("1Collection").matches()); // can't start with digit
    assertFalse(EntityAppIdLookup.LABEL_RE.matcher("").matches());
  }

  @Test
  void allowedLabelsAreNonEmpty() {
    // Sanity check — if this trips, something stripped the set.
    assertEquals(true, EntityAppIdLookup.ALLOWED_LABELS.size() > 20);
  }
}
