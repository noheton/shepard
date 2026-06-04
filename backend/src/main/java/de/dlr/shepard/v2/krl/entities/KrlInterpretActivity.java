package de.dlr.shepard.v2.krl.entities;

import de.dlr.shepard.provenance.entities.Activity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * KRL-INTERPRETER-05 — {@code :KrlInterpretActivity} typed PROV-O Activity.
 *
 * <p>Subclasses the base {@link Activity} to add the KRL-interpret-specific
 * audit fields (interpreter version, IK solver convergence stats, source /
 * URDF file appIds, scene appId, warning + unsupported-construct counts).
 *
 * <h2>Persistence shape</h2>
 * <p>Neo4j stores this as a node carrying <em>both</em> labels
 * {@code :Activity} <em>and</em> {@code :KrlInterpretActivity} (the
 * OGM-native multi-label inheritance pattern). All inherited
 * {@code :Activity} properties (appId, actionKind, agentUsername,
 * sourceMode, ikSolver*, …) and PROV-O edges (WAS_ASSOCIATED_WITH,
 * USED, GENERATED) come from the base; this subclass <em>only</em> adds
 * KRL-specific fields.
 *
 * <p>The migration story is additive — pre-existing {@code :Activity}
 * nodes are unaffected; querying for "all KRL interpret activities"
 * uses the {@code :KrlInterpretActivity} label as the discriminator.
 * Per the "Always: schema changes are additive and nullable" rule, no
 * schema migration is required for this class to land.
 *
 * <h2>Why a subclass + not a properties bag</h2>
 * <p>The {@code :KrlInterpretActivity} label makes audit queries cheap
 * ({@code MATCH (a:KrlInterpretActivity) WHERE a.ikFailedPoses > 0})
 * without needing a parallel {@code :ActivityKind} property + index.
 * Per the "Always: every persisted entity carries a single stable
 * shepardId" rule, the {@code appId} is inherited from the base —
 * the subclass does not mint a separate identifier.
 *
 * <p>See {@code aidocs/integrations/117-krl-interpreter.md §7.1} for the
 * canonical edge + property set.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
public class KrlInterpretActivity extends Activity {

  /** appId of the source {@code .src} FileReference (one of the USED edges' targets). */
  @Property("srcFileAppId")
  private String srcFileAppId;

  /** appId of the URDF FileReference (one of the USED edges' targets). */
  @Property("urdfFileAppId")
  private String urdfFileAppId;

  /**
   * Optional appId of a scene context the interpret was driven against
   * (historically a {@code :DigitalTwinScene}; post V2CONV-B4 a MAPPING_RECIPE
   * scene-graph-play template appId — KRL's own scene binding converges in
   * V2CONV-B5). {@code null} when the caller supplied frames directly.
   */
  @Property("sceneAppId")
  private String sceneAppId;

  /**
   * Sidecar-reported interpreter version (e.g. {@code "0.1.0"}). The
   * EN 9100 audit needs this to reproduce a trajectory months later.
   */
  @Property("interpreterVersion")
  private String interpreterVersion;

  /** IK back-solver identifier (e.g. {@code "ikpy"}). */
  @Property("ikSolverName")
  private String ikSolverName;

  /** IK back-solver version (e.g. {@code "3.4.2"}). */
  @Property("ikSolverVersion")
  private String ikSolverVersion;

  /** Mean per-pose IK cycle time in milliseconds. */
  @Property("ikMeanCycleMs")
  private Double ikMeanCycleMs;

  /** p99 per-pose IK cycle time in milliseconds. */
  @Property("ikP99CycleMs")
  private Double ikP99CycleMs;

  /** Maximum residual position error across all solved poses (metres). */
  @Property("ikMaxResidualMeters")
  private Double ikMaxResidualMeters;

  /** Number of poses where IK failed to converge inside the configured tolerance. */
  @Property("ikFailedPoses")
  private Integer ikFailedPoses;

  /** Total poses presented to the IK solver. */
  @Property("ikTotalPoses")
  private Integer ikTotalPoses;

  /** Number of warnings the sidecar emitted on this run. */
  @Property("warningCount")
  private Integer warningCount;

  /** Number of structured unsupported constructs the sidecar reported. */
  @Property("unsupportedConstructCount")
  private Integer unsupportedConstructCount;
}
