package de.dlr.shepard.plugins.unhide.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.UnhidePatch;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A7 — {@link ConfigDescriptor} for the Helmholtz Unhide integration,
 * exposed as {@code GET|PATCH /v2/admin/config/unhide}. Replaces the
 * bespoke {@code GET|PATCH /v2/admin/unhide/config} methods that were
 * deleted from
 * {@link de.dlr.shepard.plugins.unhide.resources.UnhideAdminRest}.
 *
 * <p>The harvest-key sister endpoints remain at
 * {@code /v2/admin/unhide/harvest-key/...} — those are credential
 * operations (mint + rotate), not config-field mutations; per CLAUDE.md
 * the ":*Config + admin REST + CLI parity" pattern explicitly keeps
 * credential endpoints as bespoke sisters.
 *
 * <p>Patchable fields: {@code enabled} (Boolean), {@code feedPublic}
 * (Boolean), {@code contactEmail} (String or explicit null to clear).
 * Attempting to patch {@code harvestApiKeyHash} throws a 400
 * {@link ConfigPatchException} — use
 * {@code POST /v2/admin/unhide/harvest-key/rotate} to mint or rotate.
 *
 * @see UnhideConfigService
 * @see de.dlr.shepard.plugins.unhide.entities.UnhideConfig
 */
@ApplicationScoped
public class UnhideConfigDescriptor implements ConfigDescriptor<UnhideConfigIO> {

  /** RFC 7807 type URI for a read-only-field patch attempt. */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/unhide.config.read-only-field";

  @Inject
  UnhideConfigService service;

  @Override
  public String featureName() {
    return "unhide";
  }

  @Override
  public String description() {
    return "Helmholtz Unhide integration: feed toggle, public mode, contact email.";
  }

  @Override
  public UnhideConfigIO currentShape() {
    return UnhideConfigIO.from(service.current());
  }

  @Override
  public UnhideConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    if (patch.has("harvestApiKeyHash")) {
      Log.warnf("V2CONV-A7/unhide: rejected PATCH — read-only field 'harvestApiKeyHash' touched");
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        "Field 'harvestApiKeyHash' cannot be set via PATCH. Use " +
        "POST /v2/admin/unhide/harvest-key/rotate to mint or rotate the harvest API key."
      );
    }

    UnhidePatch svcPatch = new UnhidePatch();

    if (patch.has("enabled")) {
      JsonNode node = patch.get("enabled");
      if (node != null && !node.isNull()) {
        svcPatch.enabled = node.asBoolean();
      }
      // explicit null on a boolean primitive — leave alone (same as JupyterConfigDescriptor)
    }

    if (patch.has("feedPublic")) {
      JsonNode node = patch.get("feedPublic");
      if (node != null && !node.isNull()) {
        svcPatch.feedPublic = node.asBoolean();
      }
      // explicit null on a boolean primitive — leave alone
    }

    if (patch.has("contactEmail")) {
      svcPatch.contactEmailTouched = true;
      JsonNode node = patch.get("contactEmail");
      svcPatch.contactEmail = (node == null || node.isNull()) ? null : node.asText();
    }

    return UnhideConfigIO.from(service.patch(svcPatch));
  }
}
