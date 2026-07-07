package de.dlr.shepard.plugins.minter.datacite.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.DatacitePatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * APISIMP-MINTER-CRED-CONFIG-UNIFY / V2CONV-A7 — {@link ConfigDescriptor} for the
 * DataCite Fabrica minter, exposed as {@code GET|PATCH /v2/admin/config/minter-datacite}.
 *
 * <p>Patchable fields: {@code enabled}, {@code apiBaseUrl}, {@code handlePrefix},
 * {@code repositoryId}, {@code publisher}, {@code landingPageBase}, {@code defaultState}
 * (all nullable). The {@code password} field is a write-only credential field:
 * a non-blank string sets the credential, explicit {@code null} clears it, absent means
 * no credential change. Attempting to patch {@code passwordHash} or {@code passwordCipher}
 * directly throws a 400 {@link ConfigPatchException}.
 *
 * @see DataciteMinterConfigService
 */
@ApplicationScoped
public class DataciteConfigDescriptor implements ConfigDescriptor<DataciteMinterConfigIO> {

  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.datacite.config.read-only-field";
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/minters.datacite.config.bad-request";

  @Inject
  DataciteMinterConfigService service;

  @Override
  public String featureName() {
    return "minter-datacite";
  }

  @Override
  public String description() {
    return "DataCite Fabrica minter: enabled toggle, API base URL, handle prefix, repository ID, " +
      "publisher, landing page base URL, and default DOI state.";
  }

  @Override
  public DataciteMinterConfigIO currentShape() {
    return DataciteMinterConfigIO.from(service.current());
  }

  @Override
  public DataciteMinterConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    if (patch.has("passwordHash") || patch.has("passwordCipher")) {
      String field = patch.has("passwordHash") ? "passwordHash" : "passwordCipher";
      Log.warnf("V2CONV-A7/minter-datacite: rejected PATCH — read-only field '%s' touched", field);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        "Field '" + field + "' cannot be set via PATCH. " +
        "Use the 'password' field in this PATCH body to update the credential."
      );
    }

    // password — write-only credential field; delegates directly to the credential service.
    if (patch.has("password")) {
      JsonNode pwNode = patch.get("password");
      if (pwNode == null || pwNode.isNull()) {
        service.clearCredential("admin-config-patch");
      } else {
        String pw = pwNode.asText();
        if (pw.isBlank()) {
          throw ConfigPatchException.badRequest(
            PROBLEM_TYPE_BAD_REQUEST,
            "Empty password",
            "Field 'password' must not be blank. Omit it to leave the credential unchanged; " +
            "set it to null to clear the credential."
          );
        }
        service.setCredential(pw, "admin-config-patch");
      }
    }

    DatacitePatch svcPatch = new DatacitePatch();

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

    if (patch.has("repositoryId")) {
      svcPatch.repositoryIdTouched = true;
      JsonNode node = patch.get("repositoryId");
      svcPatch.repositoryId = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("publisher")) {
      svcPatch.publisherTouched = true;
      JsonNode node = patch.get("publisher");
      svcPatch.publisher = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("landingPageBase")) {
      svcPatch.landingPageBaseTouched = true;
      JsonNode node = patch.get("landingPageBase");
      svcPatch.landingPageBase = (node == null || node.isNull()) ? null : node.asText();
    }

    if (patch.has("defaultState")) {
      svcPatch.defaultStateTouched = true;
      JsonNode node = patch.get("defaultState");
      svcPatch.defaultState = (node == null || node.isNull()) ? null : node.asText();
    }

    return DataciteMinterConfigIO.from(service.patch(svcPatch, "admin-config-rest"));
  }
}
