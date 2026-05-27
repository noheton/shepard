package de.dlr.shepard.v2.admin.instance.entities;

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
 * FE-PROV-INSTANCE-REGISTRY — runtime-mutable instance registry singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a / ROR1
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin config").
 * One {@code :InstanceRegistry} node is seeded on first access; subsequent
 * runtime PATCHes against {@code GET/PATCH /v2/admin/instances} mutate the
 * node in place.
 *
 * <p>Field set:
 *
 * <ul>
 *   <li>{@link #instancesJson} — JSON-serialised list of
 *       {@link de.dlr.shepard.v2.admin.instance.io.RegisteredInstanceIO}
 *       records. Stored as a JSON string (same pattern as
 *       {@code ImportPlan.summaryJson}). Default: {@code "[]"} (no peer
 *       instances configured). The service layer serialises /
 *       deserialises; the entity never exposes the typed list.</li>
 * </ul>
 *
 * <p><b>RFC 7396 PATCH semantics.</b> {@code instances} is an atomic
 * array field — present (even {@code []}) = full replace; absent = leave
 * alone. No element-level merge is supported. This is standard JSON merge-
 * patch behaviour for arrays.
 *
 * <p><b>Uniqueness constraint.</b>
 * {@code V92__Add_appId_constraint_InstanceRegistry.cypher} adds
 * {@code REQUIRE n.appId IS UNIQUE} on {@code :InstanceRegistry}.
 *
 * @see de.dlr.shepard.v2.admin.instance.services.InstanceRegistryService
 * @see de.dlr.shepard.v2.admin.instance.resources.InstanceRegistryRest
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InstanceRegistry implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * JSON-serialised list of registered peer Shepard instances.
   * Format: JSON array of objects matching
   * {@link de.dlr.shepard.v2.admin.instance.io.RegisteredInstanceIO}.
   * Stored as a string in Neo4j; deserialised by the service layer.
   * {@code null} and {@code "[]"} are both treated as "empty registry".
   */
  @Property("instancesJson")
  private String instancesJson;

  /** For testing purposes only. */
  public InstanceRegistry(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof InstanceRegistry other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
