package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.provenance.entities.ProvenanceConfig;
import de.dlr.shepard.v2.admin.provenance.io.ProvenanceConfigIO;
import de.dlr.shepard.v2.admin.provenance.services.ProvenanceConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * FTOGGLE-PROV-1 — {@link ConfigDescriptor} for the provenance config singleton,
 * exposed as {@code GET|PATCH /v2/admin/config/provenance}.
 *
 * <p>Patchable fields: {@code enabled} (Boolean), {@code captureReads} (Boolean),
 * {@code retentionDays} (Long, &gt; 0). RFC-7396 semantics: absent = leave alone,
 * null = clear (revert to deploy-time default), value = replace.
 */
@ApplicationScoped
public class ProvenanceConfigDescriptor implements ConfigDescriptor<ProvenanceConfigIO> {

  static final String PROBLEM_TYPE_INVALID_RETENTION_DAYS =
    "/problems/provenance.config.invalid-retention-days";

  @Inject
  ProvenanceConfigService service;

  @Override
  public String featureName() {
    return "provenance";
  }

  @Override
  public String description() {
    return "Runtime provenance capture settings: master switch, read capture, and retention window.";
  }

  @Override
  public ProvenanceConfigIO currentShape() {
    return toIO(service.current());
  }

  @Override
  public ProvenanceConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    ProvenanceConfig current = service.current();

    boolean enabledTouched = patch.has("enabled");
    boolean captureReadsTouched = patch.has("captureReads");
    boolean retentionDaysTouched = patch.has("retentionDays");

    Boolean effectiveEnabled =
      enabledTouched ? boolOrNull(patch.get("enabled")) : current.getEnabled();
    Boolean effectiveCaptureReads =
      captureReadsTouched ? boolOrNull(patch.get("captureReads")) : current.getCaptureReads();
    Long effectiveRetentionDays =
      retentionDaysTouched ? longOrNull(patch.get("retentionDays")) : current.getRetentionDays();

    if (retentionDaysTouched && effectiveRetentionDays != null && effectiveRetentionDays <= 0) {
      Log.warnf(
        "FTOGGLE-PROV-1: rejected PATCH — invalid retentionDays %d (must be > 0)",
        effectiveRetentionDays);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_INVALID_RETENTION_DAYS,
        "Invalid retentionDays",
        "retentionDays must be greater than 0. Set to null to revert to the deploy-time default " +
        "(shepard.provenance.retention-days). Current deploy-time default: " +
        service.getDefaultRetentionDays() + "."
      );
    }

    ProvenanceConfig saved = service.patch(effectiveEnabled, effectiveCaptureReads, effectiveRetentionDays);
    return toIO(saved);
  }

  private ProvenanceConfigIO toIO(ProvenanceConfig cfg) {
    return ProvenanceConfigIO.from(
      cfg,
      service.getDefaultEnabled(),
      service.getDefaultCaptureReads(),
      service.getDefaultRetentionDays());
  }

  private static Boolean boolOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asBoolean();
  }

  private static Long longOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asLong();
  }
}
