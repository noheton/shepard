package de.dlr.shepard.v2.versioning.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * ENT1a request body for
 * {@code POST /v2/{kind}/{appId}/versions}.
 *
 * <p>Both fields are optional:
 * <ul>
 *   <li>{@code label} — when blank/null the server suggests
 *       {@code "v<newOrdinal>"} (e.g. {@code "v3"} for the third
 *       version). When supplied, validated by
 *       {@code EntityVersionService} against the allowed-shape rules
 *       (alphanumerics + dot + hyphen; ≤ 64 chars; not purely numeric
 *       to avoid confusion with ordinal).</li>
 *   <li>{@code note} — free-form operator release-note for the
 *       version. Bounded to 2 KB on the service side.</li>
 * </ul>
 *
 * <p>An empty/no-body POST is legal — it mints the next sequential
 * version with no note.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EntityVersionCreate", description = "Body for POST .../versions; both fields are optional.")
public record EntityVersionCreateIO(
  @Schema(description = "Optional user-supplied version label (default: 'v<ordinal>').") String label,
  @Schema(description = "Optional release note.") String note
) {
  /** Convenience factory for tests / call-sites that don't have a body. */
  public static EntityVersionCreateIO empty() {
    return new EntityVersionCreateIO(null, null);
  }
}
