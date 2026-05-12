package de.dlr.shepard.common.configuration.feature.toggles;

/**
 * A5a — feature toggle for the HDF5/HSDS sidecar integration
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md}).
 *
 * <p>Default OFF. When OFF, every {@code /v2/hdf-containers/...}
 * endpoint short-circuits to 404 via {@link
 * io.quarkus.resteasy.reactive.server.EndpointDisabled}, the
 * {@code HsdsClient} is never constructed, and no outbound HTTP
 * traffic is generated. Operators flip the toggle on after wiring up
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
