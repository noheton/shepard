package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.storage.entities.AutosweepConfig;
import de.dlr.shepard.v2.admin.storage.io.AutosweepConfigIO;
import de.dlr.shepard.v2.admin.storage.services.AutosweepConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * FTOGGLE-AUTOSWEEP-1 — {@link ConfigDescriptor} for the autosweep config singleton,
 * exposed as {@code GET|PATCH /v2/admin/config/autosweep}.
 *
 * <p>Patchable fields: {@code enabled} (Boolean), {@code source} (String),
 * {@code target} (String). RFC-7396 semantics: absent = leave alone,
 * null = clear (revert to deploy-time default), value = replace.
 *
 * <p>Note: {@code shepard.migration.auto-sweep.interval} is deploy-time only
 * and is not exposed here (it drives {@code @Scheduled} which cannot change at runtime).
 */
@ApplicationScoped
public class AutosweepConfigDescriptor implements ConfigDescriptor<AutosweepConfigIO> {

  @Inject
  AutosweepConfigService service;

  @Override
  public String featureName() {
    return "autosweep";
  }

  @Override
  public String description() {
    return "Runtime file-storage auto-sweep settings: master switch, source adapter, and target adapter.";
  }

  @Override
  public AutosweepConfigIO currentShape() {
    return toIO(service.current());
  }

  @Override
  public AutosweepConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    AutosweepConfig current = service.current();

    boolean enabledTouched = patch.has("enabled");
    boolean sourceTouched = patch.has("source");
    boolean targetTouched = patch.has("target");

    Boolean effectiveEnabled =
      enabledTouched ? boolOrNull(patch.get("enabled")) : current.getEnabled();
    String effectiveSource =
      sourceTouched ? stringOrNull(patch.get("source")) : current.getSource();
    String effectiveTarget =
      targetTouched ? stringOrNull(patch.get("target")) : current.getTarget();

    AutosweepConfig saved = service.patch(effectiveEnabled, effectiveSource, effectiveTarget);
    return toIO(saved);
  }

  private AutosweepConfigIO toIO(AutosweepConfig cfg) {
    return AutosweepConfigIO.from(
      cfg,
      service.getDefaultEnabled(),
      service.getDefaultSource(),
      service.getDefaultTarget());
  }

  private static Boolean boolOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asBoolean();
  }

  private static String stringOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }
}
