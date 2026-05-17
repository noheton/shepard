package de.dlr.shepard.publish.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * KIP1a publication record per {@code aidocs/66 §3-§5}. One
 * {@code :Publication} node is attached to a published entity via
 * the {@code HAS_PUBLICATION} edge:
 *
 * <pre>
 *   (e:DataObject {appId: "01HF..."})-[:HAS_PUBLICATION]->(p:Publication {pid, mintedAt, minterId})
 * </pre>
 *
 * <p>Publications are append-only by KIP convention: a forced
 * re-mint via {@code POST /publish?force=true} attaches a fresh
 * {@code :Publication} node (the most recent one is "current"); a
 * first publish on an already-published entity is idempotent and
 * returns the existing row.
 *
 * <p>The {@code pid} property carries the {@code @Index} hint so the
 * {@code /v2/.well-known/kip/{suffix}} resolver doesn't have to do a
 * label-scan to find a Publication by its PID.
 *
 * <p>KIP1a stays narrow on the field set; KIP1c/d will widen as ePIC
 * / DataCite carry richer record shapes (handle prefix, DOI suffix
 * policy, mint status, etc.). New fields land as additive nullable
 * properties — never breaking the wire shape of KIP1a clients.
 */
@NodeEntity(label = "Publication")
@Data
@NoArgsConstructor
public class Publication implements HasId, HasAppId {

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
   * The minted persistent identifier — verbatim string the active
   * minter returned (e.g.
   * {@code "shepard:dlr.de/shepard-prod:data-objects:01HF…:v1"}
   * for the {@code LocalMinter} (KIP1h), a Handle for ePIC, a DOI
   * for DataCite). Pre-KIP1h rows minted by the in-core
   * {@code MockMinter} carry the legacy
   * {@code mock:shepard:<kind>:<appId>:<epoch-millis>} format and
   * keep resolving cleanly via the public {@code .well-known/kip}
   * resolver — the V31 backfill preserves them verbatim.
   *
   * <p>{@code @Index} so the {@code .well-known/kip/{pid-suffix}}
   * resolver lookup is O(1) — see
   * {@link de.dlr.shepard.publish.daos.PublicationDAO#findByPid(String)}.
   */
  @Index
  @Property("pid")
  private String pid;

  /**
   * Server-side wall-clock at the moment the minter returned, in
   * epoch milliseconds. Stamped from {@link de.dlr.shepard.publish.minter.MintResult#mintedAt()}.
   */
  @Property("mintedAt")
  private Long mintedAt;

  /**
   * Identifier of the minter that produced the row — verbatim
   * {@link de.dlr.shepard.publish.minter.Minter#id()} of the active
   * adapter at mint time ({@code "local"} / {@code "epic"} /
   * {@code "datacite"}; pre-KIP1h rows carry {@code "mock"} from
   * the in-core {@code MockMinter}). Kept on the row so an operator
   * can trace "which minter minted this PID" even after the active
   * minter has been switched.
   */
  @Property("minterId")
  private String minterId;

  /**
   * KIP1h Phase-1 version number — 1-based ordinal of this
   * Publication among the entity's {@code :Publication} rows
   * ({@code 1} for the first publish, bumped to {@code 2} after the
   * first {@code force=true} re-mint, etc.). Stamped here so the
   * resolver / KIP record can emit the {@code digitalObjectVersion}
   * field without a fan-out query.
   *
   * <p>Backfilled to {@code 1} on every pre-KIP1h row by
   * {@code V31__Backfill_Publication_versionNumber.cypher}; new rows
   * always carry a non-null value. Phase 2 (ENT1 umbrella) replaces
   * this scalar with a full {@code :EntityVersion} graph but
   * preserves this property as a stable read-side denormalisation.
   */
  @Property("versionNumber")
  private Integer versionNumber;

  /**
   * Username of the publisher — the caller who hit
   * {@code POST /v2/{kind}/{appId}/publish}. PROV1 captures the
   * activity separately; this field is a denormalised read-side
   * convenience so the KIP record can render {@code rightsHolder}
   * without a fan-out join.
   */
  @Property("publishedBy")
  private String publishedBy;

  /**
   * Entity-kind label of the published entity — one of the
   * {@link de.dlr.shepard.publish.PublishableKind} ids (e.g.
   * {@code "data-objects"}, {@code "collections"}). Stamped so the
   * resolver can reconstruct the {@code landingPage} URL without
   * walking the inbound {@code HAS_PUBLICATION} edge to figure out
   * the parent entity's kind.
   */
  @Property("entityKind")
  private String entityKind;

  /**
   * AppId of the published entity. Same denormalisation rationale as
   * {@link #entityKind}.
   */
  @Property("entityAppId")
  private String entityAppId;

  /**
   * KIP1f mutability marker — {@code null} on active Publications,
   * {@code "retired"} after {@code DELETE /v2/{kind}/{appId}/publish}.
   *
   * <p>KIP records are append-only per the HMC spec, so the
   * {@code :Publication} row is never deleted. Retire is a soft-state
   * flag that callers can use to stop treating the PID as "current"
   * without destroying the audit trail or breaking the PID resolver.
   *
   * <p>"retired" is a shepard extension to the standard
   * {@code digitalObjectMutability} vocabulary (which defines
   * {@code mutable} / {@code fixed} / {@code immutable}) — it
   * conveys "this publication is no longer the operator's intent"
   * without implying the underlying bit-stream has changed.
   */
  @Property("digitalObjectMutability")
  private String digitalObjectMutability;

  /** For testing purposes only. */
  public Publication(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Publication other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
