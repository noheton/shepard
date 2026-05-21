package de.dlr.shepard.v2.importer.entities;

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
 * IMP1 — dry-run import validation plan node.
 *
 * <p>Stores the result of a {@code POST /v2/import/validate} call: a
 * canonical manifest hash, a collection fingerprint (so the plan is
 * invalidated if the target collection changes), and a commitId (the
 * plan seal) that must be presented to {@code POST /v2/import/jobs}
 * to run the actual import.
 *
 * <p>Graph shape:
 * <pre>
 *   (:ImportPlan { appId, commitId, manifestHash, collectionFingerprint,
 *                  collectionAppId, validatedBy, validatedAt, expiresAt,
 *                  status, summaryJson, warningsJson })
 * </pre>
 *
 * <p>Plans are write-once: the service creates a new node per validation
 * call (the commitId encodes the timestamp so each call yields a unique
 * plan). Status transitions (VALID → EXPIRED, VALID → USED,
 * VALID → INVALIDATED) are applied via {@link #setStatus(String)}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ImportPlan implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7 minted on save by {@code GenericDAO.createOrUpdate}. */
  @Property("appId")
  private String appId;

  /**
   * Deterministic plan seal: {@code "sha256:" + hex(sha256(manifestJson + "|" +
   * fingerprint + "|" + username + "|" + validatedAt))}.
   * Each validation call produces a unique value because {@code validatedAt}
   * (epoch millis) is part of the input.
   */
  @Property("commitId")
  private String commitId;

  /** SHA-256 of the canonical (sorted) manifest JSON string. */
  @Property("manifestHash")
  private String manifestHash;

  /**
   * SHA-256 of {@code doCount + "|" + maxCreatedAt} for the target collection.
   * If the collection changes between validate and commit, the import job
   * endpoint must reject the plan.
   */
  @Property("collectionFingerprint")
  private String collectionFingerprint;

  /** AppId of the target Collection. */
  @Property("collectionAppId")
  private String collectionAppId;

  /** Username of the user who ran the validation. */
  @Property("validatedBy")
  private String validatedBy;

  /** Epoch millis when the plan was created. */
  @Property("validatedAt")
  private Long validatedAt;

  /** Epoch millis after which {@code POST /v2/import/jobs} should reject the plan (validatedAt + 24 h). */
  @Property("expiresAt")
  private Long expiresAt;

  /**
   * Lifecycle status. One of:
   * <ul>
   *   <li>{@code VALID} — ready to commit.</li>
   *   <li>{@code EXPIRED} — past {@link #expiresAt}; queryable but not runnable.</li>
   *   <li>{@code USED} — the import was already executed.</li>
   *   <li>{@code INVALIDATED} — validation produced hard errors; no commitId was issued.</li>
   * </ul>
   */
  @Property("status")
  private String status;

  /** JSON-serialized {@code ImportPlanIO.ImportSummaryIO} (avoids nested Neo4j complexity). */
  @Property("summaryJson")
  private String summaryJson;

  /** JSON array of warning strings. */
  @Property("warningsJson")
  private String warningsJson;

  /** Testing helper. */
  public ImportPlan(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ImportPlan other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
