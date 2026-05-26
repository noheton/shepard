package de.dlr.shepard.v2.quality.entities;

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
 * TPL10 — Data Quality Requirement (DQR) as a first-class Neo4j record.
 *
 * <p>A DQR specifies a measurable, runtime-evaluable expectation on the
 * DataObjects contained in one or more Collections. Evaluation is triggered
 * explicitly via {@code POST /v2/collections/{appId}/dqr/evaluate}; results
 * are returned as a {@link de.dlr.shepard.v2.quality.io.DQRResultIO} list.
 *
 * <p>Graph shape:
 * <pre>
 *   (:DataQualityRequirement) -[:APPLIES_TO]-> (:Collection)
 * </pre>
 *
 * <p>Constraint added by {@code V67__Add_DataQualityRequirement.cypher}:
 * {@code REQUIRE n.appId IS UNIQUE} on {@code :DataQualityRequirement}.
 *
 * <p>Supported rule types:
 * <ul>
 *   <li>{@code ANNOTATION_REQUIRED} — DataObject must have a non-null attribute
 *       with the key given in {@code ruleParam}. Evaluation implemented.</li>
 *   <li>{@code NO_TIMESERIES_GAP} — timeseries channel must have no gap longer
 *       than {@code ruleParam} seconds. TODO: stub.</li>
 *   <li>{@code FILE_COUNT_MIN} — file container must have at least {@code ruleParam}
 *       files. TODO: stub.</li>
 *   <li>{@code CUSTOM_CYPHER} — arbitrary Cypher predicate in {@code ruleParam}.
 *       TODO: stub.</li>
 * </ul>
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DataQualityRequirement implements HasAppId {

  /** Closed enum of supported rule types. */
  public enum RuleType {
    /** DataObject must have a non-null attribute with the key named by {@code ruleParam}. */
    ANNOTATION_REQUIRED,
    /**
     * Every timeseries channel in the collection must have no gap longer than
     * {@code ruleParam} seconds. Evaluation not yet implemented (stub returns PASS).
     */
    NO_TIMESERIES_GAP,
    /**
     * File container must have at least {@code ruleParam} files.
     * Evaluation not yet implemented (stub returns PASS).
     */
    FILE_COUNT_MIN,
    /**
     * Arbitrary Cypher boolean predicate evaluated against each DataObject.
     * Evaluation not yet implemented (stub returns PASS).
     */
    CUSTOM_CYPHER,
  }

  /** Severity of a DQR violation. */
  public enum Severity {
    ERROR,
    WARN,
    INFO,
  }

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7 minted on save by {@code GenericDAO.createOrUpdate}. */
  @Property("appId")
  private String appId;

  /** Human-readable name, e.g. "status annotation required". */
  @Property("name")
  private String name;

  /** Optional longer description of what this requirement checks. */
  @Property("description")
  private String description;

  /**
   * The rule type — drives which evaluator is selected.
   * Stored as the enum's {@code name()} string.
   */
  @Property("ruleType")
  private String ruleType;

  /**
   * Type-specific parameter.
   * <ul>
   *   <li>ANNOTATION_REQUIRED → attribute key name (e.g. {@code "status"}).</li>
   *   <li>NO_TIMESERIES_GAP  → maximum gap in seconds (e.g. {@code "5"}).</li>
   *   <li>FILE_COUNT_MIN     → minimum file count (e.g. {@code "1"}).</li>
   *   <li>CUSTOM_CYPHER      → Cypher predicate fragment.</li>
   * </ul>
   */
  @Property("ruleParam")
  private String ruleParam;

  /** ERROR / WARN / INFO. Stored as the enum's {@code name()} string. */
  @Property("severity")
  private String severity;

  /**
   * When {@code false} this DQR is ignored during evaluation.
   * Defaults to {@code true} on creation.
   */
  @Property("enabled")
  private boolean enabled;

  /** Testing helper — lets tests wire up an OGM id directly. */
  public DataQualityRequirement(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DataQualityRequirement other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
