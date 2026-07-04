package de.dlr.shepard.v2.admin.hdf;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.data.hdf.entities.HdfConfig;
import de.dlr.shepard.data.hdf.io.HdfConfigIO;
import de.dlr.shepard.data.hdf.services.HdfConfigService;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * FTOGGLE-HDF-ENABLE-1 — {@link ConfigDescriptor} for the HDF/HSDS feature,
 * exposed as {@code GET|PATCH /v2/admin/config/hdf}.
 *
 * <p>Patchable field: {@code enabled} (Boolean). RFC-7396 semantics:
 * absent = leave alone, null = clear (revert to deploy-time default), value = replace.
 *
 * <p>Note: the deploy-time {@code @LookupIfProperty} gate on {@link de.dlr.shepard.data.hdf.hsds.HsdsClient}
 * means the runtime toggle can only <em>disable</em> the feature (when
 * {@code shepard.hdf.enabled=true} at startup). Re-enabling from off
 * still requires a restart with {@code shepard.hdf.enabled=true}.
 */
@ApplicationScoped
public class HdfConfigDescriptor implements ConfigDescriptor<HdfConfigIO> {

  @Inject
  HdfConfigService service;

  @Override
  public String featureName() {
    return "hdf";
  }

  @Override
  public String description() {
    return "Runtime enable/disable switch for the HDF5/HSDS storage plugin.";
  }

  @Override
  public HdfConfigIO currentShape() {
    return toIO(service.current());
  }

  @Override
  public HdfConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    HdfConfig current = service.current();

    Boolean effectiveEnabled = patch.has("enabled")
        ? boolOrNull(patch.get("enabled"))
        : current.getEnabled();

    HdfConfig saved = service.patch(effectiveEnabled);
    return toIO(saved);
  }

  private HdfConfigIO toIO(HdfConfig cfg) {
    return HdfConfigIO.from(cfg, service.getDefaultEnabled());
  }

  private static Boolean boolOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asBoolean();
  }
}
