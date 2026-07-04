package de.dlr.shepard.plugins.v1compat.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A7 — {@link ConfigDescriptor} for the v1-compat plugin runtime config,
 * exposed as {@code GET|PATCH /v2/admin/config/legacy-v1}. Replaces the bespoke
 * {@code GET|PATCH /v2/admin/legacy/v1/config} methods deleted from
 * {@code LegacyV1ConfigAdminRest}.
 *
 * <p>Patchable fields: {@code enabled} (Boolean, absent=leave alone),
 * {@code suppressDeprecationHeaders} (Boolean, absent=leave alone).
 * Actor is passed as {@code null} — the ProvenanceCaptureFilter records the
 * caller identity on the generated {@code :Activity} node for audit purposes.
 */
@ApplicationScoped
public class LegacyV1ConfigDescriptor implements ConfigDescriptor<LegacyV1ConfigIO> {

  @Inject
  LegacyV1ConfigService service;

  @Override
  public String featureName() {
    return "legacy-v1";
  }

  @Override
  public String description() {
    return "v1 API compat surface: enabled toggle and deprecation-header suppression.";
  }

  @Override
  public LegacyV1ConfigIO currentShape() {
    return LegacyV1ConfigIO.from(service.current());
  }

  @Override
  public LegacyV1ConfigIO applyMergePatch(JsonNode patch) {
    if (patch.has("enabled")) {
      JsonNode node = patch.get("enabled");
      if (node != null && !node.isNull()) {
        service.setEnabled(node.asBoolean(), null);
      }
    }
    if (patch.has("suppressDeprecationHeaders")) {
      JsonNode node = patch.get("suppressDeprecationHeaders");
      if (node != null && !node.isNull()) {
        service.setSuppressDeprecationHeaders(node.asBoolean(), null);
      }
    }
    return LegacyV1ConfigIO.from(service.current());
  }
}
