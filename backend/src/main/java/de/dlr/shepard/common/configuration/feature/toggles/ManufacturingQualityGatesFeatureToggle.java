package de.dlr.shepard.common.configuration.feature.toggles;

import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * MFG2 — feature toggle for the manufacturing quality predecessor gate.
 *
 * <p>When enabled, {@code DataObjectService.createDataObject} checks each
 * predecessor's status before creating a successor: if any predecessor is in
 * {@code NCR_OPEN} or {@code ON_HOLD}, the create is rejected with HTTP 409.
 *
 * <p>Default is {@code false} (opt-in). Operators enable via:
 * <pre>
 *   shepard.features.manufacturing-quality-gates.enabled=true
 * </pre>
 * or at runtime via {@code PATCH /v2/admin/features/manufacturing-quality-gates}.
 */
public class ManufacturingQualityGatesFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.features.manufacturing-quality-gates.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles" +
    ".ManufacturingQualityGatesFeatureToggle#isEnabled";

  /**
   * @return {@code true} when the manufacturing quality predecessor gate is
   *         active; {@code false} (default) otherwise.
   */
  public static boolean isEnabled() {
    Optional<Boolean> value = ConfigProvider.getConfig().getOptionalValue(TOGGLE_PROPERTY, Boolean.class);
    return value.orElse(false);
  }

  private ManufacturingQualityGatesFeatureToggle() {
    // static utility — never instantiated
  }
}
