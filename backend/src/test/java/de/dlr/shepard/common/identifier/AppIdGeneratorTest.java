package de.dlr.shepard.common.identifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies the L2a UUID v7 generator produces canonical, version-7-shaped,
 * collision-free, time-ordered identifiers. The generator is the only place
 * appId values are minted on the write side, so its contract is load-bearing.
 */
public class AppIdGeneratorTest {

  @Test
  public void next_returnsCanonicalUuidString() {
    var id = AppIdGenerator.next();
    assertNotNull(id);
    assertEquals(36, id.length(), () -> "expected 36-char canonical UUID, got: " + id);
    // Parsing as java.util.UUID succeeds iff the string is canonical.
    assertDoesNotThrow(() -> UUID.fromString(id));
  }

  @Test
  public void next_isVersion7() {
    // The version nibble lives at index 14 (0-based) in the canonical form
    // 8-4-4-4-12: positions 0..7, 9..12, 14..17, 19..22, 24..35. Index 14 is
    // the first character of the third group.
    for (var i = 0; i < 50; i++) {
      var id = AppIdGenerator.next();
      var versionChar = id.charAt(14);
      assertEquals('7', versionChar, () -> "expected UUID v7 (version nibble '7'), got: " + id);
      // java.util.UUID also exposes version() — corroborate.
      assertEquals(7, UUID.fromString(id).version());
    }
  }

  @Test
  public void next_isUnique_acrossManyCalls() {
    var n = 1000;
    Set<String> seen = new HashSet<>(n * 2);
    for (var i = 0; i < n; i++) {
      var id = AppIdGenerator.next();
      assertTrue(seen.add(id), () -> "duplicate UUID v7 minted: " + id);
    }
    assertEquals(n, seen.size());
  }

  @Test
  public void next_isMonotonicOnTimePrefix() {
    // UUID v7 embeds a millisecond Unix timestamp in the high 48 bits.
    // Two calls in quick succession either share the same millisecond
    // (so the time prefix is equal) or the second is strictly greater.
    // Successive ids should never go backwards on the time prefix.
    var prev = AppIdGenerator.next();
    for (var i = 0; i < 200; i++) {
      var curr = AppIdGenerator.next();
      assertNotEquals(prev, curr, "UUIDs must be unique");
      var prevPrefix = timePrefix(prev);
      var currPrefix = timePrefix(curr);
      var fromForLog = prev;
      var toForLog = curr;
      assertTrue(
        currPrefix >= prevPrefix,
        () -> "UUID v7 time prefix went backwards: " + fromForLog + " -> " + toForLog
      );
      prev = curr;
    }
  }

  /**
   * Extracts the high 48 bits of a UUID v7 as the embedded millisecond Unix
   * timestamp. Per RFC 9562 §5.7, this is the high 48 bits of the most
   * significant 64-bit half.
   */
  private static long timePrefix(String uuid) {
    var msb = UUID.fromString(uuid).getMostSignificantBits();
    return msb >>> 16;
  }
}
