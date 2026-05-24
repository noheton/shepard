package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class MigrationChainStatusTest {

  @Test
  public void healthyFactory_buildsValidStatus() {
    MigrationChainStatus s = MigrationChainStatus.healthy(42L);

    assertTrue(s.healthy());
    assertEquals("VALID", s.outcome());
    assertTrue(s.pendingVersions().isEmpty());
    assertTrue(s.warnings().isEmpty());
    assertNull(s.errorMessage());
    assertEquals(42L, s.checkedAtEpochMs());
  }

  @Test
  public void checkFailedFactory_buildsDownStatusWithError() {
    MigrationChainStatus s = MigrationChainStatus.checkFailed("conn refused", 100L);

    assertFalse(s.healthy());
    assertEquals("CHECK_FAILED", s.outcome());
    assertEquals("conn refused", s.errorMessage());
    assertEquals(100L, s.checkedAtEpochMs());
  }

  @Test
  public void canonicalConstructor_normalisesNullCollectionsToEmpty() {
    // The compact constructor copies non-null lists and substitutes
    // empty lists for null — so consumers never NPE on .stream() etc.
    MigrationChainStatus s = new MigrationChainStatus(false, "X", null, null, null, 0L);
    assertTrue(s.pendingVersions().isEmpty());
    assertTrue(s.warnings().isEmpty());
  }

  @Test
  public void canonicalConstructor_preservesProvidedLists() {
    MigrationChainStatus s = new MigrationChainStatus(
      false,
      "DIFFERENT_CONTENT",
      List.of("61"),
      List.of("warning"),
      "bad",
      99L
    );
    assertEquals(List.of("61"), s.pendingVersions());
    assertEquals(List.of("warning"), s.warnings());
    assertEquals("bad", s.errorMessage());
  }
}
