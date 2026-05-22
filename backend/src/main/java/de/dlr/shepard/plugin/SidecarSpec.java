package de.dlr.shepard.plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PM1f — declarative description of an infrastructure sidecar that a
 * {@link PluginManifest} needs to function (an S3 backend, a Kafka
 * broker, a Redis cache, an external indexer, …).
 *
 * <p>The rule (memory {@code feedback_plugins_declare_sidecars.md}):
 * <strong>plugins declare their sidecars; the deploy assembles
 * compose; hand-edited compose overrides are forbidden.</strong>
 *
 * <p>The worked example for the first concrete consumer
 * ({@code FileS3PluginManifest} → Garage) lives in
 * {@code aidocs/integrations/93-mffd-import-v15-requirements.md §9}.
 *
 * <p>{@link SidecarsAssembler} walks the active plugins, collects
 * every {@code SidecarSpec} declared via
 * {@link PluginManifest#sidecars()}, and renders a {@code services:}
 * compose snippet an operator can paste into a
 * {@code docker-compose.override.yml}.
 *
 * <h2>Templating</h2>
 *
 * Three placeholder forms are honoured at render time (resolved by
 * the operator-side bootstrap, not by this record):
 *
 * <ul>
 *   <li>{@code {{generate:hex:N}}} — generate N hex characters of
 *       random entropy on first activation, persist to a secrets
 *       file, reuse on subsequent renders. Used for one-shot
 *       credentials like {@code GARAGE_RPC_SECRET}.</li>
 *   <li>{@code {{sidecar.host}}} — resolves to the docker-compose
 *       service name of this sidecar at render time (the operator's
 *       deploy hostname for the sidecar).</li>
 *   <li>{@code {{from:postInit.N.field}}} — resolves to the value
 *       of {@code field} captured from the N-th post-init shell
 *       command's structured output (e.g. an access key id printed
 *       by {@code /garage key new --name ...}).</li>
 * </ul>
 *
 * <p>The placeholders are intentionally string-shaped — the
 * declaration is portable across compose, kubernetes, nomad, plain
 * systemd, and the operator-side renderer picks whatever shape
 * fits the target.
 *
 * @param id stable sidecar id within the owning plugin, lowercase
 *   hyphen-separated (e.g. {@code "garage"}). The operator-side
 *   bootstrap uses this as the compose service name suffix
 *   ({@code shepard-<id>}).
 * @param image OCI image reference with explicit tag (no
 *   {@code latest} — version-pin everything per ADR-0020).
 * @param ports network ports the sidecar exposes; each carries a
 *   role label so the bootstrap can wire them to the right
 *   shepard config keys.
 * @param volumes named volumes the sidecar mounts for durable
 *   state (data dirs, certs, etc.).
 * @param env environment variables to set on the sidecar, with
 *   templating supported per the rules above.
 * @param healthcheck the readiness probe the bootstrap waits on
 *   before running {@link #postInit()}.
 * @param postInit shell commands run against the sidecar after it
 *   becomes healthy — bucket creation, key generation, schema
 *   bootstrap, etc. Each command's structured output is captured
 *   so {@code {{from:postInit.N.field}}} placeholders can reach
 *   it.
 * @param backendEnvBinding env vars to inject into the shepard
 *   backend container (so the active plugin sees the sidecar's
 *   endpoint / credentials). Templating supported per the rules
 *   above. The compose snippet emitted by {@link SidecarsAssembler}
 *   includes these in the backend service's environment block as
 *   {@code # backend-env:} comment hints (the operator-side
 *   bootstrap moves them to the backend service definition).
 */
public record SidecarSpec(
  String id,
  String image,
  List<PortSpec> ports,
  List<VolumeSpec> volumes,
  Map<String, String> env,
  HealthcheckSpec healthcheck,
  List<String> postInit,
  Map<String, String> backendEnvBinding
) {
  public SidecarSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(image, "image");
    if (id.isBlank()) {
      throw new IllegalArgumentException("sidecar id must be non-blank");
    }
    if (image.isBlank()) {
      throw new IllegalArgumentException("sidecar image must be non-blank");
    }
    ports = ports == null ? List.of() : List.copyOf(ports);
    volumes = volumes == null ? List.of() : List.copyOf(volumes);
    env = env == null ? Map.of() : Map.copyOf(env);
    postInit = postInit == null ? List.of() : List.copyOf(postInit);
    backendEnvBinding = backendEnvBinding == null
      ? Map.of()
      : Map.copyOf(backendEnvBinding);
  }
}
