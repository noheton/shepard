package de.dlr.shepard.plugin;

import java.util.Objects;

/**
 * PM1f — one named volume a {@link SidecarSpec} mounts for durable
 * state.
 *
 * <p>The named-volume shape (rather than a bind mount) is deliberate
 * — it lets the operator-side renderer pick the appropriate concrete
 * shape per substrate (docker named volume, kubernetes
 * PersistentVolumeClaim, nomad host-volume, plain {@code /var/lib/...}
 * for systemd). Plugins must not declare host paths; that's a
 * deploy-time concern.
 *
 * @param name volume name, lowercase hyphen- or underscore-separated
 *   (e.g. {@code "garage_data"}, {@code "kafka-logs"}). The
 *   bootstrap is free to prefix with the deployment id for
 *   uniqueness.
 * @param mountpoint absolute path inside the sidecar container
 *   where the volume gets mounted (e.g. {@code "/var/lib/garage"}).
 */
public record VolumeSpec(String name, String mountpoint) {
  public VolumeSpec {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(mountpoint, "mountpoint");
    if (name.isBlank()) {
      throw new IllegalArgumentException("volume name must be non-blank");
    }
    if (!mountpoint.startsWith("/")) {
      throw new IllegalArgumentException(
        "mountpoint must be absolute (start with '/') — was " + mountpoint
      );
    }
  }
}
