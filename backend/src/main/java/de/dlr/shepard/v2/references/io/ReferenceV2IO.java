package de.dlr.shepard.v2.references.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-A2 — the single, polymorphic wire shape for the unified
 * {@code /v2/references} surface. Carries the common
 * {@link BasicReferenceIO} fields (appId, name, type, dataObjectId,
 * created/updated audit) plus three discriminators and a deterministic
 * per-kind {@code payload} map.
 *
 * <h2>Discriminators</h2>
 *
 * <ul>
 *   <li>{@link #kind} — the payload family, derived from the entity:
 *       {@code file | timeseries | uri | video | git | structured |
 *       dataobject | collection | hdf}. Never a numeric id.</li>
 *   <li>{@link #referenceShape} — for FileReferences, distinguishes
 *       {@code singleton} (FR1b {@code :SingletonFileReference}) from
 *       {@code bundle} (legacy {@code :FileReference}). {@code null} for
 *       non-file kinds. Load-bearing: the frontend tells singleton from
 *       bundle by this field.</li>
 *   <li>{@link #fileKind} — only populated for {@code kind=file}
 *       singletons (e.g. {@code krl}, {@code urdf}, {@code pdf}).</li>
 * </ul>
 *
 * <h2>Why a payload map, not flattened fields</h2>
 *
 * <p>The per-kind field sets ({@code uri}/{@code relationship} for URI,
 * the time-window + alignment fields for timeseries, the embedded file
 * for singletons, repoUrl/ref/path for git, …) differ per kind, and
 * plugin kinds add their own. A flat union would couple core's IO to
 * plugin field names. The {@code payload} map keeps the envelope
 * deterministic and kind-agnostic: each handler writes its documented
 * key set into {@code payload}, and the frontend reads
 * {@code payload.uri}, {@code payload.start}, etc. by kind. The key set
 * per kind is documented in {@code docs/reference/references.md}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(
  name = "ReferenceV2",
  description = "Unified, polymorphic wire shape for every reference kind under /v2/references."
)
public class ReferenceV2IO extends BasicReferenceV2IO {

  @Schema(
    description =
      "Payload family of this reference: file | timeseries | uri | video | git | " +
      "structured | dataobject | collection | hdf. Derived from the entity; never numeric."
  )
  private String kind;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    description =
      "For kind=file: 'singleton' (FR1b) vs 'bundle' (legacy multi-file). null for other kinds."
  )
  private String referenceShape;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    description =
      "For kind=file singletons: the file-kind discriminator (krl, svdx, otvis, urdf, xit, pdf). " +
      "null otherwise or when unrecognised."
  )
  private String fileKind;

  @Schema(
    description =
      "Per-kind field payload. Deterministic key set per kind (see docs/reference/references.md): " +
      "uri kind → {uri, relationship}; timeseries kind → {start, end, timeseriesContainerId, " +
      "timeReference, wallClockOffset, wallClockOffsetSource, qualityScore, lastScoredAt}; " +
      "file kind → {file}; git kind → {repoUrl, ref, path, mode}."
  )
  private Map<String, Object> payload = new LinkedHashMap<>();

  /**
   * Base constructor that copies the common BasicReference fields and the
   * derived {@link #kind}. Handlers call this, then populate
   * {@link #referenceShape}, {@link #fileKind}, and {@link #payload}.
   *
   * @param ref the persisted entity.
   * @param kind the resolved kind token.
   */
  public ReferenceV2IO(BasicReference ref, String kind) {
    super(ref);
    this.kind = kind;
  }

  /** Convenience: put a payload field. Null values are preserved (RFC 7396 friendliness). */
  public ReferenceV2IO put(String key, Object value) {
    this.payload.put(key, value);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    ReferenceV2IO other = (ReferenceV2IO) o;
    return Objects.equals(kind, other.kind)
      && Objects.equals(referenceShape, other.referenceShape)
      && Objects.equals(fileKind, other.fileKind)
      && Objects.equals(payload, other.payload);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hashCode(kind);
    result = prime * result + Objects.hashCode(referenceShape);
    result = prime * result + Objects.hashCode(fileKind);
    result = prime * result + Objects.hashCode(payload);
    return result;
  }
}
