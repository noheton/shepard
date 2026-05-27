package de.dlr.shepard.context.collection.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.Collection;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Collection")
public class CollectionIO extends AbstractDataObjectIO {

  @Schema(readOnly = true, required = true)
  private long[] dataObjectIds;

  @Schema(readOnly = true, required = true)
  private long[] incomingIds;

  /**
   * Id of a default file container.
   * This value can be nullable.
   * The default value is null.
   */
  @Schema(nullable = true)
  private Long defaultFileContainerId = null;

  /**
   * Optional hero/banner image URL displayed at the top of the Collection
   * detail page. When null, no banner is shown. URL-only (no server-side
   * upload); the frontend handles 404s gracefully via the {@code <v-img>}
   * error slot. Settable on POST and PATCH (RFC 7396: null clears it,
   * absent leaves it unchanged).
   *
   * <p>Originally advertised as {@code /v2/}-only, but because
   * {@link CollectionIO} is shared with the v1 {@code /shepard/api/collections}
   * surface and Jackson serialises a null Java field as JSON {@code null}
   * by default, the field was leaking onto the v1 wire as
   * {@code "heroImageUrl": null}. That violates the byte-fidelity
   * compat policy in {@code CLAUDE.md §"API-version policy"} (upstream
   * 5.2.0 has no such key). The {@link JsonInclude} annotation below
   * omits the key entirely when null, restoring byte-fidelity on v1
   * while keeping it visible on v2 when set.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String heroImageUrl;

  /**
   * Optional free-text label describing the origin of this Collection,
   * e.g. "tapelaying", "bridgewelding", "v15-redrive-1". Intended to
   * disambiguate multiple Collections with the same {@code name} that
   * were created by successive import runs (NEO-AUDIT-007).
   *
   * <p>Same {@code @JsonInclude(NON_NULL)} treatment as {@code heroImageUrl}:
   * {@link CollectionIO} is shared with the v1 {@code /shepard/api/collections}
   * surface. Without this annotation, a null value would leak onto the v1 wire
   * as {@code "importedFrom": null}, violating the byte-fidelity compat policy
   * (upstream 5.2.0 has no such key). The annotation omits the key entirely
   * when null, keeping v1 byte-identical to upstream while surfacing the field
   * on v2 when set.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String importedFrom;

  /**
   * PROMPT-h2 — per-Collection PromptLog storage mode.
   *
   * <p>Controls how AI conversation bodies are persisted in the PromptLog
   * substrate for this Collection. Values correspond to
   * {@link de.dlr.shepard.context.collection.entities.PromptLogMode}:
   * {@code "HASH_ONLY"} (safe default — only a SHA-256 hash stored),
   * {@code "BODY_REDACTED"} (body stored after PII redaction),
   * {@code "BODY_RAW"} (body stored verbatim — air-gapped / GPAI doc only).
   *
   * <p>Null in the wire representation means "not yet set; treat as
   * {@code HASH_ONLY}". After the {@code V91} migration all existing
   * Collections carry an explicit {@code "HASH_ONLY"}, so GET responses
   * for migrated instances will always show the effective mode.
   *
   * <p>Same {@code @JsonInclude(NON_NULL)} treatment as {@code heroImageUrl}:
   * the field is omitted from the v1 {@code /shepard/api/} wire when null,
   * keeping the upstream-5.2.0 byte-fidelity guarantee intact.
   *
   * <p>See {@code aidocs/semantics/99-promptlog-design.md §10-11}.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"HASH_ONLY", "BODY_REDACTED", "BODY_RAW"})
  private String promptLogMode;

  public CollectionIO(Collection collection) {
    super(collection);
    this.dataObjectIds = extractShepardIds(collection.getDataObjects());
    this.incomingIds = extractShepardIds(collection.getIncoming());

    if (collection.getFileContainer() == null) {
      this.defaultFileContainerId = null;
    } else {
      this.defaultFileContainerId = collection.getFileContainer().getId();
    }

    this.heroImageUrl = collection.getHeroImageUrl();
    this.importedFrom = collection.getImportedFrom();
    this.promptLogMode = collection.getPromptLogMode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof CollectionIO)) return false;
    CollectionIO other = (CollectionIO) o;
    return (
      HasId.areEqualSets(dataObjectIds, other.dataObjectIds) &&
      HasId.areEqualSets(incomingIds, other.incomingIds) &&
      Objects.equals(defaultFileContainerId, other.defaultFileContainerId) &&
      Objects.equals(heroImageUrl, other.heroImageUrl) &&
      Objects.equals(importedFrom, other.importedFrom) &&
      Objects.equals(promptLogMode, other.promptLogMode)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObjectIds);
    result = prime * result + HasId.hashcodeHelper(incomingIds);
    result = prime * result + Objects.hashCode(defaultFileContainerId);
    result = prime * result + Objects.hashCode(heroImageUrl);
    result = prime * result + Objects.hashCode(importedFrom);
    result = prime * result + Objects.hashCode(promptLogMode);
    return result;
  }
}
