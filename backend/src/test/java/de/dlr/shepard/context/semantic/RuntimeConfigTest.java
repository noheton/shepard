package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tiny coverage for the {@link RuntimeConfig} record. */
class RuntimeConfigTest {

  @Test
  void deployTimeOnly_isOnWithNoDisables() {
    RuntimeConfig rc = RuntimeConfig.deployTimeOnly();
    assertTrue(rc.preseedEnabled());
    assertTrue(rc.disabledBundles().isEmpty());
  }

  @Test
  void of_emptyList_yieldsEmptySet() {
    RuntimeConfig rc = RuntimeConfig.of(true, List.of());
    assertTrue(rc.preseedEnabled());
    assertTrue(rc.disabledBundles().isEmpty());
  }

  @Test
  void of_nullList_yieldsEmptySet() {
    RuntimeConfig rc = RuntimeConfig.of(false, null);
    assertFalse(rc.preseedEnabled());
    assertTrue(rc.disabledBundles().isEmpty());
  }

  @Test
  void of_listPreservesEntries() {
    RuntimeConfig rc = RuntimeConfig.of(true, List.of("foaf", "qudt"));
    assertEquals(2, rc.disabledBundles().size());
    assertTrue(rc.disabledBundles().contains("foaf"));
    assertTrue(rc.disabledBundles().contains("qudt"));
  }

  @Test
  void disabledBundlesIsImmutable() {
    RuntimeConfig rc = RuntimeConfig.of(true, List.of("foaf"));
    assertThrows(UnsupportedOperationException.class, () -> rc.disabledBundles().add("qudt"));
  }

  @Test
  void canonicalCtor_nullDisabled_normalisedToEmpty() {
    RuntimeConfig rc = new RuntimeConfig(true, null);
    assertTrue(rc.disabledBundles().isEmpty());
  }
}
