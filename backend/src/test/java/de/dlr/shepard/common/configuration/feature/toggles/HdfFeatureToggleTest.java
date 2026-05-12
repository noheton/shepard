package de.dlr.shepard.common.configuration.feature.toggles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Lean unit test for {@link HdfFeatureToggle#isActive()}: pokes the
 * underlying system property the toggle reads and checks the gate
 * flips both ways. No Quarkus boot, no SmallRyeConfig re-register
 * dance — MicroProfile Config falls back to system properties when
 * no explicit source overrides them.
 */
class HdfFeatureToggleTest {

  private static final String KEY = "shepard.hdf.enabled";
  private String previousValue;

  @BeforeEach
  void capturePrior() {
    previousValue = System.getProperty(KEY);
  }

  @AfterEach
  void restorePrior() {
    if (previousValue == null) {
      System.clearProperty(KEY);
    } else {
      System.setProperty(KEY, previousValue);
    }
  }

  @Test
  void isActiveReturnsFalseWhenUnset() {
    System.clearProperty(KEY);
    assertFalse(HdfFeatureToggle.isActive(), "default-off");
  }

  @Test
  void isActiveReturnsFalseWhenExplicitFalse() {
    System.setProperty(KEY, "false");
    assertFalse(HdfFeatureToggle.isActive());
  }

  @Test
  void isActiveReturnsTrueWhenSetToTrue() {
    System.setProperty(KEY, "true");
    assertTrue(HdfFeatureToggle.isActive());
  }
}
