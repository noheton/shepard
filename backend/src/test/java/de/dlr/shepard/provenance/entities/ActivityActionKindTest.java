package de.dlr.shepard.provenance.entities;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ActivityActionKind} — specifically the app-layer
 * validation that Neo4j Community Edition cannot enforce via a constraint.
 * Part of NEO-AUDIT-015.
 */
class ActivityActionKindTest {

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "READ", "UPDATE", "DELETE", "EXECUTE"})
  void canonicalEnumValuesPassValidation(String value) {
    assertDoesNotThrow(() -> ActivityActionKind.validate(value),
      "Expected canonical value '" + value + "' to pass validation");
  }

  @Test
  void aiActionExtensionValuePassesValidation() {
    // AI_ACTION is a TPL9 extension value written by AiProvenanceCapture.
    // It is in ALL_KNOWN_VALUES but not the enum, so validate() must accept it.
    assertDoesNotThrow(() -> ActivityActionKind.validate("AI_ACTION"),
      "Expected AI_ACTION (TPL9 extension) to pass validation");
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "CREAT", "WRITE", "MODIFY", "UNKNOWN", "", "null"})
  void unknownValueThrowsIllegalArgumentException(String value) {
    assertThrows(IllegalArgumentException.class,
      () -> ActivityActionKind.validate(value),
      "Expected unknown value '" + value + "' to throw IllegalArgumentException");
  }

  @Test
  void nullValueThrowsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
      () -> ActivityActionKind.validate(null),
      "Expected null to throw IllegalArgumentException");
  }

  @Test
  void allKnownValuesSetContainsAllEnumMembers() {
    for (ActivityActionKind kind : ActivityActionKind.values()) {
      assertTrue(
        ActivityActionKind.ALL_KNOWN_VALUES.contains(kind.name()),
        "ALL_KNOWN_VALUES must contain enum member: " + kind.name()
      );
    }
  }
}
