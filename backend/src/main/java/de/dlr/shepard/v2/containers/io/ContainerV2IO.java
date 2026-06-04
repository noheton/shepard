package de.dlr.shepard.v2.containers.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2CONV-A3 — the single, polymorphic wire shape for the unified
 * {@code /v2/containers} surface. The direct sibling of
 * {@code ReferenceV2IO} (V2CONV-A2). Carries the common
 * {@link BasicContainerIO} fields (appId, name, type, status, created/updated
 * audit) plus a {@code kind} discriminator and a deterministic per-kind
 * {@code payload} map.
 *
 * <h2>Discriminator</h2>
 *
 * <ul>
 *   <li>{@link #kind} — the container family, derived from the entity:
 *       {@code file | timeseries | structured-data | hdf}. Never a numeric id.</li>
 * </ul>
 *
 * <h2>Why a payload map, not flattened fields</h2>
 *
 * <p>The per-kind read-only field sets differ per kind ({@code file} carries
 * {@code oid} + {@code defaultCollectionIdList}; {@code structured-data}
 * carries {@code oid}; {@code timeseries} carries none today; plugin kinds add
 * their own). A flat union would couple core's IO to plugin field names. The
 * {@code payload} map keeps the envelope deterministic and kind-agnostic: each
 * handler writes its documented key set into {@code payload}, and the frontend
 * reads {@code payload.oid} etc. by kind. The key set per kind is documented in
 * {@code docs/reference/containers.md}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(
  name = "ContainerV2",
  description = "Unified, polymorphic wire shape for every container kind under /v2/containers."
)
public class ContainerV2IO extends BasicContainerIO {

  @Schema(
    description =
      "Container family of this container: file | timeseries | structured-data | hdf. " +
      "Derived from the entity; never numeric."
  )
  private String kind;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    description =
      "Per-kind read-only field payload. Deterministic key set per kind (see " +
      "docs/reference/containers.md): file kind → {oid, defaultCollectionIdList}; " +
      "structured-data kind → {oid}; timeseries kind → {} (no extra fields today)."
  )
  private Map<String, Object> payload = new LinkedHashMap<>();

  /**
   * Base constructor that copies the common BasicContainer fields and the
   * derived {@link #kind}. Handlers call this, then populate {@link #payload}.
   *
   * @param container the persisted entity.
   * @param kind the resolved kind token.
   */
  public ContainerV2IO(BasicContainer container, String kind) {
    super(container);
    this.kind = kind;
  }

  /** Convenience: put a payload field. Null values are preserved. */
  public ContainerV2IO put(String key, Object value) {
    this.payload.put(key, value);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    ContainerV2IO other = (ContainerV2IO) o;
    return Objects.equals(kind, other.kind) && Objects.equals(payload, other.payload);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hashCode(kind);
    result = prime * result + Objects.hashCode(payload);
    return result;
  }
}
