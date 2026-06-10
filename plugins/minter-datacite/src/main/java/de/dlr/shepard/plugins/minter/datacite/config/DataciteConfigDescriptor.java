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
 * V2CONV-A7 — {@link ConfigDescriptor} for the DataCite Fabrica minter,
 * exposed as {@code GET|PATCH /v2/admin/config/minter-datacite}. Replaces
 * the bespoke {@code GET|PATCH /v2/admin/minters/datacite/config} methods
 * that were deleted from
 * {@link de.dlr.shepard.plugins.minter.datacite.resources.DataciteAdminRest}.
 *
 * <p>The credential sister endpoints remain at
 * {@code /v2/admin/minters/datacite/credential} — those are credential
 * operations (set + rotate), not config-field mutations.
 *
 * <p>Patchable fields: {@code enabled}, {@code apiBaseUrl},
 * {@code handlePrefix}, {@code repositoryId}, {@code publisher},
 * {@code landingPageBase}, {@code defaultState} (all nullable). Attempting
 * to patch {@code passwordHash} or {@code passwordCipher} throws a 400
 * {@link ConfigPatchException} — use
 * {@code POST /v2/admin/minters/datacite/credential} to set the password.
 *
 * @see DataciteMinterConfigService
 */
@ApplicationScoped
public class DataciteConfigDescriptor implements ConfigDescriptor<DataciteMinterConfigIO> {

  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.datacite.config.read-only-field";

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
        "Use POST /v2/admin/minters/datacite/credential to set the password."
      );
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
