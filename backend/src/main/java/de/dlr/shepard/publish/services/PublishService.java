package de.dlr.shepard.publish.services;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.MinterRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * KIP1a service — coordinates a publish call end-to-end: builds a
 * {@link MintRequest} from the entity's metadata, asks the active
 * {@link de.dlr.shepard.publish.minter.Minter} to mint a PID, persists
 * a {@link Publication} row, attaches the
 * {@code HAS_PUBLICATION} edge, and returns the saved row.
 *
 * <p>Idempotency: a second call on an already-published entity
 * returns the existing most-recent Publication (no mint). With
 * {@code force=true}, a fresh PID is minted and attached as an
 * additional row — the most recent one is "current" per the
 * append-only KIP convention.
 *
 * <p>Permission gates live in the REST layer (the caller is the
 * authoritative source of who's calling); this service trusts its
 * caller has already verified Write/Manage on the target entity.
 */
@RequestScoped
public class PublishService {

  @Inject
  MinterRegistry minterRegistry;

  @Inject
  PublicationDAO publicationDAO;

  @Inject
  EntityIdResolver entityIdResolver;

  /** Result returned to the REST layer. */
  public record PublishOutcome(Publication publication, boolean fresh) {}

  /**
   * Publish the entity addressed by {@code (kind, entityAppId)}.
   *
   * @param kind         the publishable kind (resolved from the URL segment)
   * @param entityAppId  the entity's appId
   * @param locatorUrl   the URL the PID should resolve to (i.e.
   *                     {@code <shepard.url>/v2/{kind}/{appId}})
   * @param publishedBy  username of the caller (stamped on the row
   *                     for rights-holder display)
   * @param force        when {@code true}, always mint a fresh PID;
   *                     when {@code false}, return the existing
   *                     most-recent Publication (if any) without minting
   * @return outcome carrying the Publication and a flag indicating
   *         whether a fresh mint happened
   * @throws NotFoundException if the entity doesn't exist
   * @throws de.dlr.shepard.publish.minter.MinterException if the mint fails
   */
  public PublishOutcome publish(
    PublishableKind kind,
    String entityAppId,
    String locatorUrl,
    String publishedBy,
    boolean force
  ) {
    if (kind == null) throw new IllegalArgumentException("kind must not be null");
    if (entityAppId == null || entityAppId.isBlank()) {
      throw new IllegalArgumentException("entityAppId must not be null/blank");
    }
    // Verify the entity exists + carries the expected label; throws
    // NotFoundException if missing.
    verifyEntityKind(kind, entityAppId);

    // Idempotency check — if not forced and a Publication already
    // exists, return the most-recent one without minting.
    if (!force) {
      List<Publication> existing = publicationDAO.findByEntityAppId(entityAppId);
      if (!existing.isEmpty()) {
        Publication current = existing.get(0);
        Log.debugf(
          "PublishService: idempotent publish for entityAppId=%s — returning existing PID %s",
          entityAppId,
          current.getPid()
        );
        return new PublishOutcome(current, false);
      }
    }

    Map<String, String> metadata = buildMetadata(kind, entityAppId);
    MintRequest req = new MintRequest(kind.urlSegment(), entityAppId, locatorUrl, metadata);
    MintResult result = minterRegistry.activeMinter().mint(req);

    Publication pub = new Publication();
    pub.setPid(result.pid());
    pub.setMintedAt(result.mintedAt().toEpochMilli());
    pub.setMinterId(result.minterId());
    pub.setPublishedBy(publishedBy);
    pub.setEntityKind(kind.urlSegment());
    pub.setEntityAppId(entityAppId);
    Publication saved = publicationDAO.attachToEntity(pub, entityAppId);
    Log.infof(
      "PublishService: published entityAppId=%s as PID=%s via minter=%s (force=%s)",
      entityAppId,
      saved.getPid(),
      saved.getMinterId(),
      force
    );
    return new PublishOutcome(saved, true);
  }

  /**
   * Verify the entity at {@code entityAppId} carries the Neo4j label
   * matching {@code kind}. Throws {@link NotFoundException} with an
   * operator-readable message when the entity doesn't exist or has a
   * different kind.
   */
  void verifyEntityKind(PublishableKind kind, String entityAppId) {
    Session session = session();
    if (session == null) {
      throw new NotFoundException("Neo4j session not available; cannot verify entity " + entityAppId);
    }
    String query = "MATCH (e:" + kind.neo4jLabel() + " {appId: $appId}) RETURN e.appId AS appId LIMIT 1";
    var result = session.query(query, Map.of("appId", entityAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) {
      throw new NotFoundException(
        "No " + kind.neo4jLabel() + " entity with appId " + entityAppId + " exists in this instance"
      );
    }
  }

  /**
   * Build the metadata map handed to the {@link MintRequest}. KIP1a
   * baseline collects the fields the KIP record needs: digital-object
   * type, name, created/modified timestamps, rights-holder, license.
   *
   * <p>License is currently null per {@code aidocs/66 §3}'s "if
   * applicable" semantics — the field is reserved for the KIP1e UI
   * flow that lets a user pick a license on publish.
   */
  Map<String, String> buildMetadata(PublishableKind kind, String entityAppId) {
    Map<String, String> meta = new LinkedHashMap<>();
    meta.put("digitalObjectType", kind.digitalObjectType());

    Session session = session();
    if (session == null) return Map.copyOf(meta);

    // Pull name + audit timestamps + createdBy username in one query.
    // We use a generic match keyed by appId so we don't depend on the
    // OGM entity class graph for kinds we haven't loaded.
    String query =
      "MATCH (e:" +
      kind.neo4jLabel() +
      " {appId: $appId}) " +
      "OPTIONAL MATCH (e)-[:created_by]->(u:User) " +
      "RETURN e.name AS name, e.createdAt AS createdAt, e.updatedAt AS updatedAt, " +
      "u.username AS createdByUsername LIMIT 1";
    var result = session.query(query, Map.of("appId", entityAppId));
    var iter = result.iterator();
    if (iter.hasNext()) {
      var row = iter.next();
      Object name = row.get("name");
      if (name != null) meta.put("name", String.valueOf(name));
      Object createdAt = row.get("createdAt");
      if (createdAt instanceof Number n) {
        meta.put("dateCreated", java.time.Instant.ofEpochMilli(n.longValue()).toString());
      }
      Object updatedAt = row.get("updatedAt");
      if (updatedAt instanceof Number n) {
        meta.put("dateModified", java.time.Instant.ofEpochMilli(n.longValue()).toString());
      }
      Object createdBy = row.get("createdByUsername");
      if (createdBy != null) meta.put("rightsHolder", String.valueOf(createdBy));
    }
    return Map.copyOf(meta);
  }

  /**
   * Resolve the most-recent Publication for an entity. Used by the
   * idempotency path + by callers that want to surface "current
   * publication" alongside the entity.
   */
  public Optional<Publication> currentFor(String entityAppId) {
    List<Publication> all = publicationDAO.findByEntityAppId(entityAppId);
    return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
  }

  /**
   * Indirection so tests can wire a mocked Session in without
   * spinning the static NeoConnector singleton.
   */
  Session session() {
    return NeoConnector.getInstance().getNeo4jSession();
  }
}
