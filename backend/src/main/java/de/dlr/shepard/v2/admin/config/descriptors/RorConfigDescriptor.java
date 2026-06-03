package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the instance ROR config singleton,
 * exposed as {@code GET|PATCH /v2/admin/config/ror}. Replaces the bespoke
 * {@code InstanceRorConfigRest} (deleted). Delegates unchanged to
 * {@link InstanceRorConfigService}; the {@code :InstanceRorConfig} entity and
 * service are untouched.
 *
 * <p>Patchable fields: {@code rorId} (1-9 alphanumeric chars when non-null),
 * {@code organizationName}. RFC-7396 semantics: absent = leave alone, null =
 * clear, value = replace.
 */
@ApplicationScoped
public class RorConfigDescriptor implements ConfigDescriptor<InstanceRorConfigIO> {

  /** RFC 7807 type URI for the invalid-rorId path. */
  static final String PROBLEM_TYPE_INVALID_ROR_ID = "/problems/ror.config.invalid-ror-id";

  /** Lenient pattern: 1-9 alphanumeric chars (canonicality enforcement deferred). */
  static final String ROR_ID_PATTERN = "[A-Za-z0-9]{1,9}";

  @Inject
  InstanceRorConfigService service;

  @Override
  public String featureName() {
    return "ror";
  }

  @Override
  public String description() {
    return "Instance-level Research Organization Registry (ROR) identifier and organization name.";
  }

  @Override
  public InstanceRorConfigIO currentShape() {
    return InstanceRorConfigIO.from(service.current());
  }

  @Override
  public InstanceRorConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    InstanceRorConfig current = service.current();

    boolean rorIdTouched = patch.has("rorId");
    boolean orgTouched = patch.has("organizationName");

    String effectiveRorId = rorIdTouched ? textOrNull(patch.get("rorId")) : current.getRorId();
    String effectiveOrgName = orgTouched ? textOrNull(patch.get("organizationName")) : current.getOrganizationName();

    if (rorIdTouched && effectiveRorId != null && !effectiveRorId.isBlank() && !effectiveRorId.matches(ROR_ID_PATTERN)) {
      Log.warnf("V2CONV-A4/ror: rejected PATCH — invalid rorId '%s' (must match %s)", effectiveRorId, ROR_ID_PATTERN);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_INVALID_ROR_ID,
        "Invalid ROR identifier",
        "rorId must be 1-9 alphanumeric characters (a-z, A-Z, 0-9). " +
        "Provide the ROR suffix only — not the full URL. " +
        "E.g. '04cvxnb49' for DLR, not 'https://ror.org/04cvxnb49'. " +
        "Omit or set to null to clear the existing value."
      );
    }

    InstanceRorConfig saved = service.patch(effectiveRorId, effectiveOrgName);
    return InstanceRorConfigIO.from(saved);
  }

  private static String textOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }
}
