package de.dlr.shepard.common.configuration.feature.toggles;

/**
 * A5a — feature toggle for the HDF5/HSDS sidecar integration
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md}).
 *
 * <p>Default OFF. When OFF, the {@code HsdsClient} is never
 * constructed and no outbound HTTP traffic is generated; the hdf
 * admin surface short-circuits to 404 via {@link
 * io.quarkus.resteasy.reactive.server.EndpointDisabled}, and the hdf
 * container handler on the unified {@code /v2/containers} surface
 * fails soft (its HSDS-backed operations surface an operator-readable
 * error). Operators flip the toggle on after wiring up
 * the {@code hdf} compose profile and supplying HSDS HTTP Basic
 * credentials (Phase 1) — see {@code docs/admin.md} §"HDF5 (HSDS)".
 *
 * <p>Mirrors the shape of {@link SpatialDataFeatureToggle}.
 */
public class HdfFeatureToggle {

  private static final String HDF_ENABLED_PROPERTY = "shepard.hdf.enabled";

  private HdfFeatureToggle() {
    // utility class — no instances
  }

  public static boolean isActive() {
    return TogglePropertyUtil.isToggleEnabled(HDF_ENABLED_PROPERTY);
  }
}
