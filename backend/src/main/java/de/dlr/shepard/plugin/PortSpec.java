package de.dlr.shepard.plugin;

import java.util.Objects;

/**
 * PM1f — one network port a {@link SidecarSpec} exposes.
 *
 * <p>The {@code role} label is what lets the operator-side bootstrap
 * wire the right port to the right shepard config key — e.g. a Garage
 * sidecar exposes 3900 with role {@code "s3-api"} and 3902 with role
 * {@code "web-admin"}; the bootstrap sees {@code role="s3-api"} and
 * binds {@code SHEPARD_FILES_S3_ENDPOINT} to that port.
 *
 * @param port container port number (1-65535). The operator-side
 *   renderer is free to map the host-side publish port differently.
 * @param role human-readable role label, lowercase hyphen-separated
 *   (e.g. {@code "s3-api"}, {@code "web-admin"}, {@code "metrics"}).
 *   The {@link SidecarSpec#backendEnvBinding()} keys reference roles
 *   indirectly via {@code {{sidecar.host}}} (the host portion is
 *   resolved per-role at render time).
 */
public record PortSpec(int port, String role) {
  public PortSpec {
    Objects.requireNonNull(role, "role");
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException(
        "port must be in [1, 65535] — was " + port
      );
    }
    if (role.isBlank()) {
      throw new IllegalArgumentException("role must be non-blank");
    }
  }
}
