package de.dlr.shepard.common.openapi;

import java.util.List;

/**
 * Single source of truth for "is this OpenAPI path on the v1 shelf, the
 * v2 shelf, or neither?" — used by {@link V1OpenApiFilter} and
 * {@link V2OpenApiFilter}.
 *
 * <p><b>Path shapes accepted.</b> At unit-test time, fixtures use raw
 * paths as JAX-RS reports them ({@code /shepard/api/foo}, {@code /v2/bar},
 * {@code /healthz}). At runtime the build-time
 * {@link de.dlr.shepard.common.filters.ApiPathFilter} has already
 * stripped the {@code /shepard/api} prefix, so the model holds shapes
 * like {@code /foo}, {@code /v2/bar}, {@code /healthz}. Both modes
 * route correctly through these classifiers.
 *
 * <p><b>Platform paths.</b> {@code /healthz}, {@code /openapi},
 * {@code /swagger-ui}, {@code /metrics} are server-internal: they
 * belong to neither shelf. A generated v1/v2 client must not see them.
 */
final class OpenApiShelfMembership {

  /**
   * Non-application / platform-level prefixes that belong to neither
   * API shelf. Each entry matches either an exact path or a path
   * starting with the entry followed by {@code /} — so {@code /healthz}
   * matches but {@code /healthzfoo} does not.
   *
   * <p>Note: {@code /openapi.json} and {@code /openapi.yaml} are
   * handled separately (see {@link #PLATFORM_DOTTED}) — the combined
   * OpenAPI document smallrye serves uses the dotted suffix, which
   * the prefix-plus-slash rule above does not catch.
   */
  private static final List<String> PLATFORM_PREFIXES = List.of("/healthz", "/openapi", "/swagger-ui", "/metrics");

  /**
   * Exact-match platform paths that don't follow the prefix-plus-slash
   * shape. These are the Quarkus-served entry points for the combined
   * spec / swagger-ui assets.
   */
  private static final List<String> PLATFORM_DOTTED = List.of(
    "/openapi.json",
    "/openapi.yaml",
    "/openapi.yml"
  );

  private static final String V1_RAW_PREFIX = "/shepard/api";
  private static final String V2_PREFIX = "/v2";

  private OpenApiShelfMembership() {
    // utility
  }

  /**
   * A path belongs to the v1 (upstream-compatible) shelf if it is not
   * a {@code /v2/} path and not a platform path. This catches both raw
   * {@code /shepard/api/...} paths and the stripped form produced by
   * {@link de.dlr.shepard.common.filters.ApiPathFilter}.
   */
  static boolean isV1Path(String path) {
    if (path == null || path.isEmpty()) return false;
    if (isPlatformPath(path)) return false;
    if (matchesPrefix(path, V2_PREFIX)) return false;
    return true;
  }

  /**
   * A path belongs to the v2 (fork's development) shelf iff it starts
   * with {@code /v2/} (or is exactly {@code /v2}). Platform paths are
   * not v2.
   */
  static boolean isV2Path(String path) {
    if (path == null || path.isEmpty()) return false;
    if (isPlatformPath(path)) return false;
    return matchesPrefix(path, V2_PREFIX);
  }

  private static boolean isPlatformPath(String path) {
    for (String prefix : PLATFORM_PREFIXES) {
      if (matchesPrefix(path, prefix)) return true;
    }
    for (String dotted : PLATFORM_DOTTED) {
      if (path.equals(dotted)) return true;
    }
    return false;
  }

  private static boolean matchesPrefix(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  /**
   * Visible to the OpenAPI-internal tests that want to assert against
   * the same raw-path constant the filters use.
   */
  static String v1RawPrefix() {
    return V1_RAW_PREFIX;
  }
}
