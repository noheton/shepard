package de.dlr.shepard.common.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutputProfileTest {

  @Test
  void parseCanonicalNames() {
    assertEquals(OutputProfile.METADATA, OutputProfile.parse("metadata"));
    assertEquals(OutputProfile.RELATIONS, OutputProfile.parse("relations"));
    assertEquals(OutputProfile.ALL, OutputProfile.parse("all"));
  }

  @Test
  void parseIsCaseInsensitive() {
    assertEquals(OutputProfile.METADATA, OutputProfile.parse("Metadata"));
    assertEquals(OutputProfile.RELATIONS, OutputProfile.parse("RELATIONS"));
    assertEquals(OutputProfile.ALL, OutputProfile.parse("All"));
  }

  @Test
  void parseTrimsWhitespace() {
    assertEquals(OutputProfile.METADATA, OutputProfile.parse("  metadata  "));
  }

  @Test
  void parseEmptyReturnsDefault() {
    assertEquals(OutputProfile.DEFAULT, OutputProfile.parse(null));
    assertEquals(OutputProfile.DEFAULT, OutputProfile.parse(""));
    assertEquals(OutputProfile.DEFAULT, OutputProfile.parse("   "));
  }

  @Test
  void parseUnknownReturnsNull() {
    assertNull(OutputProfile.parse("everything"));
    assertNull(OutputProfile.parse("compact"));
  }

  @Test
  void defaultIsAll() {
    assertEquals(OutputProfile.ALL, OutputProfile.DEFAULT);
  }

  @Test
  void validNamesContainsAllValues() {
    String names = OutputProfile.validNames();
    assertTrue(names.contains("metadata"));
    assertTrue(names.contains("relations"));
    assertTrue(names.contains("all"));
  }
}
