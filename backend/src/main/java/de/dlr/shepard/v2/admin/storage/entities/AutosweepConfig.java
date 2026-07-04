package de.dlr.shepard.v2.admin.storage.entities;

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
 * FTOGGLE-AUTOSWEEP-1 — runtime-mutable autosweep config singleton.
 *
 * <p>Single Neo4j node seeded on first startup from deploy-time defaults
 * ({@code shepard.migration.auto-sweep.*}); runtime PATCHes via
 * {@code PATCH /v2/admin/config/autosweep} mutate this node in place.
 *
 * <p>Field set:
 * <ul>
 *   <li>{@link #enabled} — master switch; {@code null} → default {@code false}.</li>
 *   <li>{@link #source} — source adapter id; {@code null} → default {@code ""}.</li>
 *   <li>{@link #target} — target adapter id; {@code null} → default {@code ""}.</li>
 * </ul>
 *
 * <p>Note: {@code shepard.migration.auto-sweep.interval} stays deploy-time only
 * (it is resolved by {@code @Scheduled} at startup and cannot change at runtime).
 *
 * <p>Constraint: {@code V117__Add_appId_constraint_AutosweepConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AutosweepConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /** Master auto-sweep switch. {@code null} → deploy-time default ({@code false}). */
  @Property("enabled")
  private Boolean enabled;

  /** Source storage adapter id. {@code null} → deploy-time default ({@code ""}). */
  @Property("source")
  private String source;

  /** Target storage adapter id. {@code null} → deploy-time default ({@code ""}). */
  @Property("target")
  private String target;

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AutosweepConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
