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
 * APISIMP-MINTER-CRED-CONFIG-UNIFY / V2CONV-A7 — {@link ConfigDescriptor} for the
 * ePIC handle service minter, exposed as {@code GET|PATCH /v2/admin/config/minter-epic}.
 *
 * <p>Patchable fields: {@code enabled} (Boolean), {@code apiBaseUrl} (String or explicit
 * null to clear), {@code handlePrefix} (String or explicit null to clear). The
 * {@code credential} field is a write-only credential field: a non-blank string sets the
 * credential, explicit {@code null} clears it, absent means no credential change.
 * Attempting to patch {@code credentialHash} or {@code credentialKey} directly throws
 * a 400 {@link ConfigPatchException}.
 *
 * @see EpicMinterConfigService
 */
@ApplicationScoped
public class EpicConfigDescriptor implements ConfigDescriptor<EpicMinterConfigIO> {

  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.epic.config.read-only-field";
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/minters.epic.config.bad-request";

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
        "Use the 'credential' field in this PATCH body to update the credential."
      );
    }

    // credential — write-only credential field; delegates directly to the credential service.
    if (patch.has("credential")) {
      JsonNode credNode = patch.get("credential");
      if (credNode == null || credNode.isNull()) {
        service.clearCredential("admin-config-patch");
      } else {
        String cred = credNode.asText();
        if (cred.isBlank()) {
          throw ConfigPatchException.badRequest(
            PROBLEM_TYPE_BAD_REQUEST,
            "Empty credential",
            "Field 'credential' must not be blank. Omit it to leave the credential unchanged; " +
            "set it to null to clear the credential."
          );
        }
        service.setCredential(cred, "admin-config-patch");
      }
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
