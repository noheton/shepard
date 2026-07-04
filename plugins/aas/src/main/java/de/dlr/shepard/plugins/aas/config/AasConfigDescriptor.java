package de.dlr.shepard.plugins.aas.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.aas.io.AasConfigIO;
import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasConfigService.AasPatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A7 — {@link ConfigDescriptor} for the AAS plugin runtime config,
 * exposed as {@code GET|PATCH /v2/admin/config/aas}. Replaces the bespoke
 * {@code GET|PATCH /v2/admin/aas/config} methods that were deleted from
 * {@code AasConfigAdminRest}.
 *
 * <p>Patchable fields: {@code enabled} (Boolean, absent=leave alone,
 * explicit-null=leave alone), {@code registryUrl} / {@code registryApiKey} /
 * {@code baseUrl} (tri-state: absent=leave, null=clear, string=replace).
 */
@ApplicationScoped
public class AasConfigDescriptor implements ConfigDescriptor<AasConfigIO> {

  @Inject
  AasConfigService service;

  @Override
  public String featureName() {
    return "aas";
  }

  @Override
  public String description() {
    return "AAS plugin: registry URL, API key, base URL, and enabled toggle.";
  }

  @Override
  public AasConfigIO currentShape() {
    return AasConfigIO.from(service.current());
  }

  @Override
  public AasConfigIO applyMergePatch(JsonNode patch) {
    AasPatch svcPatch = new AasPatch();

    if (patch.has("enabled")) {
      JsonNode node = patch.get("enabled");
      if (node != null && !node.isNull()) {
        svcPatch.enabled = node.asBoolean();
      }
    }

    if (patch.has("registryUrl")) {
      svcPatch.registryUrlTouched = true;
      JsonNode node = patch.get("registryUrl");
      svcPatch.registryUrl = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("registryApiKey")) {
      svcPatch.registryApiKeyTouched = true;
      JsonNode node = patch.get("registryApiKey");
      svcPatch.registryApiKey = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("baseUrl")) {
      svcPatch.baseUrlTouched = true;
      JsonNode node = patch.get("baseUrl");
      svcPatch.baseUrl = (node == null || node.isNull()) ? null : node.asText();
    }

    return AasConfigIO.from(service.patch(svcPatch));
  }
}
