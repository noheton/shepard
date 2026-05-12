package de.dlr.shepard.data.hdf.entities;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Properties;

/**
 * A5a Phase 1 — Neo4j node representing one HDF5/HSDS container
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §8).
 *
 * <p>Each {@code HdfContainer} is a thin shepard-side handle for an
 * HSDS <em>domain</em>. The actual chunked HDF5 byte payload lives in
 * the HSDS sidecar's underlying object/POSIX store; shepard only
 * persists the routing metadata (the {@link #hsdsDomain} path) plus
 * the standard {@link BasicContainer} envelope (appId, name,
 * permissions, audit-trail).
 *
 * <p>Phase 1 (this slice) supports create / read / delete only. The
 * read/write data-path mirroring of HSDS's
 * {@code /datasets/{id}/value} surface, the per-DataObject
 * {@code HdfReference}, the byte-identical download fallback, and the
 * shared-Keycloak token relay all arrive in later phases (A5b–A5e).
 *
 * <p><strong>Permission model.</strong> Standard
 * {@link BasicContainer} ACL (owner / readers / writers / managers) via
 * {@code permissions}; A5b will flow these onto the HSDS domain ACL
 * via a {@code PermissionsService} post-commit hook. In Phase 1 the
 * HSDS side stays at its admin default, and any reads/writes of HDF
 * payload itself go via the shared admin credential (out of band of
 * shepard).
 */
@NodeEntity(label = "HdfContainer")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HdfContainer extends BasicContainer {

  /**
   * Free-form description shown alongside the container in the UI.
   * Optional; clients may leave it {@code null}.
   */
  private String description;

  /**
   * The HSDS domain path provisioned for this container — for
   * example {@code /shepard/<container-appId>/}. Stored verbatim so
   * subsequent broker calls can compute the HSDS REST URL without
   * round-tripping shepard's other metadata.
   *
   * <p>Set by {@code HdfContainerService} on create after the HSDS
   * {@code PUT /} (provision-domain) returns 201. Never mutated
   * afterwards on the shepard side; HSDS is the storage authority.
   */
  private String hsdsDomain;

  /**
   * Free-form key/value metadata. Same {@code @Properties(delimiter
   * = "||")} idiom as the rest of the codebase (post-V9) so search
   * + indexing apply uniformly across primitives.
   */
  @ToString.Exclude
  @Properties(delimiter = "||")
  private Map<String, String> attributes = new HashMap<>();

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public HdfContainer(long id) {
    super(id);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(description, hsdsDomain, attributes);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof HdfContainer)) return false;
    HdfContainer other = (HdfContainer) obj;
    return (
      Objects.equals(description, other.description) &&
      Objects.equals(hsdsDomain, other.hsdsDomain) &&
      Objects.equals(attributes, other.attributes)
    );
  }
}
