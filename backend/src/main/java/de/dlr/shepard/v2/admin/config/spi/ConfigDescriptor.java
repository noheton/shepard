package de.dlr.shepard.v2.admin.config.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * V2CONV-A4 — descriptor SPI for a single runtime-configurable feature exposed
 * through the generic admin-config surface
 * {@code GET|PATCH /v2/admin/config/{feature}}.
 *
 * <p>Each runtime-configurable feature (semantic, jupyter, sql-timeseries, ror,
 * …) contributes one {@code ConfigDescriptor} bean. The descriptor is the only
 * thing that knows the feature's shape; the generic
 * {@link de.dlr.shepard.v2.admin.config.resources.AdminConfigRest} resource
 * resolves a descriptor by {@link #featureName()} and delegates, centralising
 * the {@code @RolesAllowed("instance-admin")} role gate, the RFC-7396
 * merge-patch contract, the {@code application/problem+json} error envelope and
 * the PROV1a {@code :Activity} capture. Adding a new configurable feature no
 * longer needs a new REST class — only a new descriptor bean.
 *
 * <p><b>Why a raw {@link JsonNode} for the patch?</b> RFC 7396 merge-patch is
 * tri-state — an <em>absent</em> member means "leave alone", an explicit
 * {@code null} means "clear", a value means "replace". A typed DTO cannot
 * distinguish "absent" from "null"; the per-feature bespoke IO classes solved
 * this with {@code @JsonSetter(nulls = SET)} + {@code *Touched} flags, but the
 * generic resource cannot know the feature's field set ahead of time. Passing
 * the parsed {@code JsonNode} preserves the absent/null/value distinction and
 * lets each descriptor map it onto its own service exactly as the bespoke REST
 * class did. (Quarkus' built-in {@code application/merge-patch+json} filter has
 * known Jackson interop issues — quarkus#33186 / #37980 — so we keep the
 * explicit, well-tested per-field mapping rather than relying on it.)
 *
 * <p>Registries in this repo are <b>fail-soft</b>: a descriptor never throws on
 * registration, and an unknown {@code {feature}} yields a 404 problem-JSON
 * rather than a 500. Validation failures inside {@link #applyMergePatch} are
 * signalled by throwing {@link ConfigPatchException}, which the resource maps to
 * the appropriate 4xx problem-JSON.
 *
 * @param <T> the read-projection IO type returned by {@link #currentShape()} and
 *            {@link #applyMergePatch} (e.g. {@code SemanticConfigIO})
 */
public interface ConfigDescriptor<T> {

  /**
   * The {@code {feature}} path segment that selects this descriptor — e.g.
   * {@code "semantic"}, {@code "jupyter"}, {@code "sql-timeseries"},
   * {@code "ror"}. Must be unique across all descriptors, URL-safe, and stable
   * (it is a public API surface).
   *
   * @return the lowercase, hyphen-separated feature key (never null/blank)
   */
  String featureName();

  /**
   * A short human-readable description of what this feature's config controls,
   * surfaced in the registry listing for operator discovery.
   *
   * @return a one-line description (never null)
   */
  String description();

  /**
   * Read the current, fully-resolved config shape for this feature. Implementations
   * delegate to the existing {@code *ConfigService} and project onto the same IO
   * type the bespoke {@code GET} returned.
   *
   * @return the current config projection (never null)
   */
  T currentShape();

  /**
   * Apply an RFC-7396 merge-patch and return the updated, fully-resolved shape.
   *
   * <p>The {@code patch} is the parsed request body. Implementations read each
   * field with {@link JsonNode#has(String)} to detect presence (absent = leave
   * alone) and {@link JsonNode#isNull()} to detect an explicit clear, exactly
   * mirroring the bespoke REST class's per-field logic. Validation failures
   * throw {@link ConfigPatchException}.
   *
   * @param patch the parsed merge-patch body (never null; an empty object is a
   *              valid no-op)
   * @return the updated config projection (never null)
   * @throws ConfigPatchException when a patched value fails validation
   */
  T applyMergePatch(JsonNode patch) throws ConfigPatchException;

  /**
   * Whether this feature's config may be read by any authenticated user via
   * {@code GET /v2/config/{feature}} (no admin role required).
   *
   * <p>Defaults to {@code false}. Override to {@code true} for features whose
   * read shape is safe to expose to all logged-in users — e.g. the JupyterHub
   * {@code enabled + hubUrl} pair that the unified data-references table polls
   * to decide whether to show the "Open in JupyterHub" affordance.
   *
   * <p>The admin write surface ({@code PATCH /v2/admin/config/{feature}}) is
   * always instance-admin-only regardless of this flag.
   *
   * @return {@code true} when any authenticated user may read this config
   */
  default boolean publicRead() {
    return false;
  }
}
