package de.dlr.shepard.auth.users.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrcidValidatorTest {

  // ─── Valid ORCID identifiers — sourced from public ORCID examples ────

  @ParameterizedTest
  @ValueSource(
    strings = {
      "0000-0002-1825-0097", // ORCID's own canonical example
      "0000-0001-5109-3700", // example from the ORCID API docs
      "0000-0002-1694-233X" // example with X check character (value 10)
    }
  )
  void acceptsKnownValidOrcids(String orcid) {
    assertTrue(OrcidValidator.isValid(orcid), () -> "expected valid: " + orcid);
  }

  // ─── Checksum mismatch (one digit flipped) ──────────────────────────

  @Test
  void rejectsChecksumMismatch() {
    // Mutate the last digit of a known-valid ORCID to break the checksum.
    assertFalse(OrcidValidator.isValid("0000-0002-1825-0098"));
    assertFalse(OrcidValidator.isValid("0000-0002-1825-0091"));
  }

  // ─── Format violations ──────────────────────────────────────────────

  @Test
  void rejectsNull() {
    assertFalse(OrcidValidator.isValid(null));
  }

  @Test
  void rejectsBlank() {
    assertFalse(OrcidValidator.isValid(""));
    assertFalse(OrcidValidator.isValid("   "));
  }

  @Test
  void rejectsWrongLength() {
    assertFalse(OrcidValidator.isValid("0000-0002-1825-009")); // missing one digit
    assertFalse(OrcidValidator.isValid("0000-0002-1825-00977")); // one digit too many
    assertFalse(OrcidValidator.isValid("0000000218250097")); // no hyphens
  }

  @Test
  void rejectsHyphensInWrongPositions() {
    assertFalse(OrcidValidator.isValid("000-00002-1825-0097"));
    assertFalse(OrcidValidator.isValid("00000-002-1825-0097"));
  }

  @Test
  void rejectsLettersInDigitPositions() {
    // The 16th character may be 'X', but no other position accepts a letter.
    assertFalse(OrcidValidator.isValid("000A-0002-1825-0097"));
    assertFalse(OrcidValidator.isValid("0000-000B-1825-0097"));
    assertFalse(OrcidValidator.isValid("0000-0002-182C-0097"));
    // Lowercase 'x' is not accepted in the check position either.
    assertFalse(OrcidValidator.isValid("0000-0002-1694-233x"));
  }

  @Test
  void rejectsWhitespaceAndUriPrefix() {
    // The validator is strict on the canonical wire shape — callers
    // strip any URI prefix before validating.
    assertFalse(OrcidValidator.isValid(" 0000-0002-1825-0097"));
    assertFalse(OrcidValidator.isValid("0000-0002-1825-0097 "));
    assertFalse(OrcidValidator.isValid("https://orcid.org/0000-0002-1825-0097"));
  }
}
