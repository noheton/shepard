package de.dlr.shepard.data.hdf.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * FTOGGLE-HDF-ENABLE-1 — runtime-mutable HDF/HSDS feature config singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / P10c / ROR1
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin config").
 * One {@code :HdfConfig} node is seeded on first startup; subsequent
 * runtime PATCHes against {@code PATCH /v2/admin/config/hdf} mutate it.
 *
 * <p>Placed in {@code de.dlr.shepard.data.hdf.entities} so that
 * {@link de.dlr.shepard.plugins.hdf5.HdfPayloadKind#entityPackages()} —
 * which returns this package — already covers OGM auto-discovery without
 * any change to {@code HdfPayloadKind}.
 *
 * <p>V115 migration adds the uniqueness constraint on {@code appId}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class HdfConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /**
   * Runtime-mutable enabled flag.
   * {@code null} → use the deploy-time default ({@code shepard.hdf.enabled}).
   */
  @Property("enabled")
  private Boolean enabled;

  /** For testing purposes only. */
  public HdfConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof HdfConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
