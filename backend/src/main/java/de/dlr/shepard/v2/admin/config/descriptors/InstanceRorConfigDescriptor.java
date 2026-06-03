package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.ConfigRegistry;
import de.dlr.shepard.v2.admin.config.ConfigValidationException;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the instance ROR config singleton.
 *
 * <p>Registers under feature name {@code "ror"} so
 * {@code GET|PATCH /v2/admin/config/ror} dispatches here.
 * Mirrors the validation logic in {@code InstanceRorConfigRest}.
 */
@ApplicationScoped
public class InstanceRorConfigDescriptor implements ConfigDescriptor {

  static final String FEATURE = "ror";
  static final String PROBLEM_INVALID_ROR_ID = "/problems/ror.config.invalid-ror-id";
  static final String ROR_ID_PATTERN = "[A-Za-z0-9]{1,9}";

  @Inject
  InstanceRorConfigService service;

  void onStart(@Observes StartupEvent event, ConfigRegistry registry) {
    registry.register(this);
  }

  @Override
  public String featureName() {
    return FEATURE;
  }

  @Override
  public Object read() {
    return InstanceRorConfigIO.from(service.current());
  }

  @Override
  public Object patch(JsonNode node) throws ConfigValidationException {
    InstanceRorConfig current = service.current();
    String effectiveRorId = current.getRorId();
    String effectiveOrgName = current.getOrganizationName();

    if (node != null && node.has("rorId")) {
      JsonNode v = node.get("rorId");
      if (v.isNull()) {
        effectiveRorId = null;
      } else {
        String val = v.asText();
        if (!val.isBlank() && !val.matches(ROR_ID_PATTERN)) {
          throw new ConfigValidationException(
            PROBLEM_INVALID_ROR_ID,
            "Invalid ROR identifier",
            "rorId must be 1-9 alphanumeric characters (a-z, A-Z, 0-9). " +
              "Provide the ROR suffix only — not the full URL (e.g. '04cvxnb49' for DLR). " +
              "Omit or set to null to clear the existing value."
          );
        }
        effectiveRorId = val.isBlank() ? null : val;
      }
    }

    if (node != null && node.has("organizationName")) {
      JsonNode v = node.get("organizationName");
      effectiveOrgName = v.isNull() ? null : v.asText();
    }

    InstanceRorConfig saved = service.patch(effectiveRorId, effectiveOrgName);
    return InstanceRorConfigIO.from(saved);
  }
}
