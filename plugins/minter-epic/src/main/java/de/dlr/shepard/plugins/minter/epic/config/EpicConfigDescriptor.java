package de.dlr.shepard.plugins.minter.epic.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.minter.epic.io.EpicMinterConfigIO;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.EpicPatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A7 — {@link ConfigDescriptor} for the ePIC handle service minter,
 * exposed as {@code GET|PATCH /v2/admin/config/minter-epic}. Replaces the
 * bespoke {@code GET|PATCH /v2/admin/minters/epic/config} methods that were
 * deleted from
 * {@link de.dlr.shepard.plugins.minter.epic.resources.EpicAdminRest}.
 *
 * <p>The credential sister endpoints remain at
 * {@code /v2/admin/minters/epic/credential} — those are credential
 * operations (set + rotate), not config-field mutations.
 *
 * <p>Patchable fields: {@code enabled} (Boolean), {@code apiBaseUrl}
 * (String or explicit null to clear), {@code handlePrefix} (String or
 * explicit null to clear). Attempting to patch {@code credentialHash} or
 * {@code credentialKey} throws a 400 {@link ConfigPatchException} — use
 * {@code POST /v2/admin/minters/epic/credential} to set the credential.
 *
 * @see EpicMinterConfigService
 */
@ApplicationScoped
public class EpicConfigDescriptor implements ConfigDescriptor<EpicMinterConfigIO> {

  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.epic.config.read-only-field";

  @Inject
  EpicMinterConfigService service;

  @Override
  public String featureName() {
    return "minter-epic";
  }

  @Override
  public String description() {
    return "ePIC handle service minter: enabled toggle, API base URL, and handle prefix.";
  }

  @Override
  public EpicMinterConfigIO currentShape() {
    return EpicMinterConfigIO.from(service.current());
  }

  @Override
  public EpicMinterConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    if (patch.has("credentialHash") || patch.has("credentialKey")) {
      String field = patch.has("credentialHash") ? "credentialHash" : "credentialKey";
      Log.warnf("V2CONV-A7/minter-epic: rejected PATCH — read-only field '%s' touched", field);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        "Field '" + field + "' cannot be set via PATCH. " +
        "Use POST /v2/admin/minters/epic/credential to set the credential."
      );
    }

    EpicPatch svcPatch = new EpicPatch();

    if (patch.has("enabled")) {
      JsonNode node = patch.get("enabled");
      if (node != null && !node.isNull()) {
        svcPatch.enabled = node.asBoolean();
      }
    }

    if (patch.has("apiBaseUrl")) {
      svcPatch.apiBaseUrlTouched = true;
      JsonNode node = patch.get("apiBaseUrl");
      svcPatch.apiBaseUrl = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("handlePrefix")) {
      svcPatch.handlePrefixTouched = true;
      JsonNode node = patch.get("handlePrefix");
      svcPatch.handlePrefix = (node == null || node.isNull()) ? null : node.asText();
    }

    return EpicMinterConfigIO.from(service.patch(svcPatch, "admin-config-rest"));
  }
}
