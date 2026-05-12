package de.dlr.shepard.common.output;

import java.util.Arrays;

/**
 * v2 output-control profile (see {@code aidocs/56 §3}). Lets callers
 * narrow the response shape via the {@code ?profile=} query parameter.
 *
 * <ul>
 *   <li>{@link #METADATA} — only the entity's own scalar fields. No
 *       relations, no nested children. Smallest payload; best for
 *       tree-view rendering.</li>
 *   <li>{@link #RELATIONS} — metadata + linked-entity {@code appId}s as
 *       opaque strings. Best for graph navigation that re-fetches each
 *       hop on demand.</li>
 *   <li>{@link #ALL} — metadata + full nested relation objects (the
 *       current shape; default for backward-compat).</li>
 * </ul>
 *
 * <p>Resolution lives in
 * {@link OutputProfileResolver}; only the {@code /v2/...} surface
 * honours the parameter — the {@code /shepard/api/...} compat surface
 * stays byte-frozen per {@code CLAUDE.md}'s API-version policy.
 */
public enum OutputProfile {
  METADATA,
  RELATIONS,
  ALL;

  /** Default when no {@code ?profile=} is supplied. */
  public static final OutputProfile DEFAULT = ALL;

  /**
   * Parse the {@code ?profile=} query value, case-insensitively.
   * Returns {@code null} on an unknown value so the caller can surface
   * a 400 with the valid-names list (per {@code aidocs/56 §3}).
   */
  public static OutputProfile parse(String raw) {
    if (raw == null || raw.isBlank()) return DEFAULT;
    for (OutputProfile p : values()) {
      if (p.name().equalsIgnoreCase(raw.trim())) return p;
    }
    return null;
  }

  /** Comma-separated list of valid profile names — for error messages. */
  public static String validNames() {
    return String.join(", ", Arrays.stream(values()).map(p -> p.name().toLowerCase()).toArray(String[]::new));
  }
}
