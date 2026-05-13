package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * N1c2 — single-instance Neo4j node carrying the runtime-mutable
 * ontology-preseed knobs (per the A3b
 * {@code :FeatureToggleRegistry} pattern + {@code aidocs/65 §2.2}).
 *
 * <p>The class layer enforces "exactly one" via
 * {@code OntologyConfigService.loadSingleton()} — first start seeds
 * the row from the deploy-time {@code shepard.semantic.internal.preseed-ontologies.*}
 * defaults; subsequent reads return the same row; subsequent writes
 * mutate it in place. The constraint on disk (V27) is L2a appId
 * uniqueness, not a singleton lock, so the invariant is "service-
 * level: every helper that touches the node reads/writes through
 * the same finder".
 *
 * <p>The {@code disabledBundles} set is the runtime-disabled bundle
 * ids. A bundle id present here AND in the manifest's
 * {@code required: true} subset is honoured as required (i.e. ignored
 * — required wins); see {@code OntologySeedService.shouldSeed}.
 * The deploy-time
 * {@code shepard.semantic.internal.preseed-ontologies.skip-bundles}
 * CSV is the install-time default and is set-unioned with this set
 * at seed time.
 *
 * @see de.dlr.shepard.context.semantic.services.OntologyConfigService
 * @see de.dlr.shepard.context.semantic.OntologySeedService
 */
@NodeEntity
@Data
@NoArgsConstructor
public class SemanticConfig implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam.
   */
  @Property("appId")
  private String appId;

  /**
   * Master toggle — when false, the ontology pre-seed pass on
   * startup is a no-op (even when the deploy-time
   * {@code shepard.semantic.internal.preseed-ontologies.enabled=true}
   * default is set). Mirrors the A3b runtime-wins precedence.
   * Default: true.
   */
  @Property("preseedEnabled")
  private boolean preseedEnabled = true;

  /**
   * Bundle ids the operator has runtime-disabled. Stored as a
   * {@code List} (the OGM serialises this to a string[]) rather than
   * a {@code Set} because Neo4j OGM's default-property mapping is
   * order-preserving on arrays — useful for audit-trail reads.
   * Service-layer reads dedupe on the boundary.
   *
   * <p>A bundle id in this list whose manifest entry has
   * {@code required: true} is still seeded — required wins over
   * runtime disable.
   */
  @Property("disabledBundles")
  private List<String> disabledBundles = new ArrayList<>();

  /** Millis since epoch when the row was first persisted. */
  @Property("createdAt")
  private Long createdAt;

  /** Millis since epoch when {@code preseedEnabled} / {@code disabledBundles} was last touched. */
  @Property("updatedAt")
  private Long updatedAt;

  /** Username of the admin who last modified the config. */
  @Property("updatedBy")
  private String updatedBy;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
