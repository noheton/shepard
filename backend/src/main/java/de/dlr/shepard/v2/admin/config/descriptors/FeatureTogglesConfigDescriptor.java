package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.io.FeatureToggleIO;
import de.dlr.shepard.v2.admin.io.FeatureTogglesConfigIO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY — exposes {@link FeatureToggleRegistry}
 * through the generic {@code GET|PATCH /v2/admin/config/feature-toggles} surface.
 *
 * <p>PATCH body is a flat JSON object whose keys are toggle names and values
 * are booleans: {@code {"versioning": true, "spatial-data": false}}.
 * RFC 7396 semantics apply: absent keys are left unchanged.
 */
@ApplicationScoped
public class FeatureTogglesConfigDescriptor implements ConfigDescriptor<FeatureTogglesConfigIO> {

  private static final String PT_UNKNOWN = "/problems/feature-toggles.unknown-toggle";
  private static final String PT_INVALID = "/problems/feature-toggles.invalid-value";

  @Inject
  FeatureToggleRegistry registry;

  @Override
  public String featureName() {
    return "feature-toggles";
  }

  @Override
  public String description() {
    return "JVM-lifetime runtime feature toggles (changes are not persisted across restarts). " +
      "PATCH body: {\"toggle-name\": true/false, ...}.";
  }

  @Override
  public FeatureTogglesConfigIO currentShape() {
    List<FeatureToggleIO> toggles = registry.list()
      .stream()
      .map(e -> new FeatureToggleIO(e.getName(), e.isEnabled(), e.getDescription(), e.getSource()))
      .toList();
    return new FeatureTogglesConfigIO(toggles);
  }

  @Override
  public FeatureTogglesConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    var it = patch.fields();
    while (it.hasNext()) {
      var field = it.next();
      String name = field.getKey();
      JsonNode value = field.getValue();
      if (registry.get(name).isEmpty()) {
        throw ConfigPatchException.badRequest(
          PT_UNKNOWN,
          "Unknown feature toggle",
          "No feature toggle registered under '" + name + "'. " +
            "List available toggles with GET /v2/admin/config/feature-toggles."
        );
      }
      if (!value.isBoolean()) {
        throw ConfigPatchException.badRequest(
          PT_INVALID,
          "Invalid toggle value",
          "Toggle '" + name + "' expects a boolean value (true/false); got: " + value.getNodeType() + "."
        );
      }
      registry.set(name, value.asBoolean());
    }
    return currentShape();
  }
}
