package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigIO;
import de.dlr.shepard.v2.admin.thermography.services.ThermographyConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * APISIMP-THERMO-ADMIN-CONFIG — {@link ConfigDescriptor} for the
 * {@code :ThermographyConfig} singleton, exposed via the generic registry as
 * {@code GET|PATCH /v2/admin/config/thermography}. Replaces the bespoke
 * {@code ThermographyConfigAdminRest} (deleted). Delegates unchanged to
 * {@link ThermographyConfigService}; the {@code :ThermographyConfig} entity and
 * service are untouched.
 *
 * <p>Patchable fields: {@code thresholdC} (double), {@code gridWidth} (int),
 * {@code gridHeight} (int). RFC-7396 semantics: absent = leave alone, null =
 * revert to deploy-time default, non-null value = replace.
 */
@ApplicationScoped
public class ThermographyConfigDescriptor implements ConfigDescriptor<ThermographyConfigIO> {

  @Inject
  ThermographyConfigService service;

  @Override
  public String featureName() {
    return "thermography";
  }

  @Override
  public String description() {
    return "Thermography analysis runtime config: quality-score threshold (°C) and heatmap grid dimensions.";
  }

  @Override
  public ThermographyConfigIO currentShape() {
    return service.getConfig();
  }

  @Override
  public ThermographyConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    var current = service.current();

    Double effectiveThresholdC = patch.has("thresholdC")
      ? (patch.get("thresholdC").isNull() ? null : patch.get("thresholdC").doubleValue())
      : current.getThresholdC();

    Integer effectiveGridWidth = patch.has("gridWidth")
      ? (patch.get("gridWidth").isNull() ? null : patch.get("gridWidth").intValue())
      : current.getGridWidth();

    Integer effectiveGridHeight = patch.has("gridHeight")
      ? (patch.get("gridHeight").isNull() ? null : patch.get("gridHeight").intValue())
      : current.getGridHeight();

    ThermographyConfigIO updated = service.patchConfig(
      effectiveThresholdC, effectiveGridWidth, effectiveGridHeight);
    Log.infof(
      "APISIMP-THERMO-ADMIN-CONFIG: PATCH /v2/admin/config/thermography "
        + "→ thresholdC=%s gridWidth=%s gridHeight=%s",
      updated.thresholdC(), updated.gridWidth(), updated.gridHeight());
    return updated;
  }
}
