package de.dlr.shepard.publish.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.publish.entities.Publication;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KIP1a DAO for {@link Publication} rows. Designed in
 * {@code aidocs/66 §3-§5}.
 *
 * <p>Reads:
 * <ul>
 *   <li>{@link #findByPid(String)} — single Cypher round-trip via
 *       the {@code @Index} on {@code pid} (cheap path for the
 *       {@code /v2/.well-known/kip/{pid-suffix}} resolver).
 *   <li>{@link #findByEntityAppId(String)} — every Publication
 *       attached to a given entity, most-recent first, used both for
 *       idempotency ("has this entity already been published?") and
 *       for surfacing the publication trail on the entity view in
 *       future slices.
 * </ul>
 *
 * <p>Writes go through {@link #attachToEntity(Publication, String)} which
 * persists the row via {@code GenericDAO#createOrUpdate} (mints the
 * {@code appId}) then attaches the {@code (entity)-[:HAS_PUBLICATION]->(publication)}
 * edge in a separate Cypher pass — the Publication and the entity
 * are wired together via the {@code entityAppId} field rather than
 * an OGM relationship so the entity classes ({@code DataObject},
 * {@code Collection}) stay untouched by KIP1a (CLAUDE.md
 * "frozen upstream classes are untouchable").
 */
@RequestScoped
public class PublicationDAO extends GenericDAO<Publication> {

  /** Constant for the {@code :HAS_PUBLICATION} edge type. */
  public static final String HAS_PUBLICATION = "HAS_PUBLICATION";

  @Override
  public Class<Publication> getEntityType() {
    return Publication.class;
  }

  /**
   * RDM-003 — return all {@code :Publication} rows across the instance,
   * ordered by {@code mintedAt DESC} (most-recent first), with pagination.
   *
   * <p>This is the backing query for
   * {@code GET /v2/admin/publications} — the instance-wide PID audit
   * list. Only instance-admins call this path; no per-entity
   * permission check is needed beyond the role gate on the REST layer.
   *
   * @param page 0-based page index
   * @param size page size (caller-supplied, typically 25)
   * @return at most {@code size} rows starting at offset {@code page * size}
   */
  public List<Publication> findAll(int page, int size) {
    if (session == null) return List.of();
    int offset = Math.max(0, page) * Math.max(1, size);
    String query =
      "MATCH (p:Publication) RETURN p ORDER BY p.mintedAt DESC " +
      "SKIP $offset LIMIT $size";
    Iterable<Publication> result = findByQuery(query, Map.of("offset", offset, "size", size));
    List<Publication> out = new ArrayList<>();
    result.forEach(out::add);
    return out;
  }

  /**
   * RDM-003 — count all {@code :Publication} rows across the instance.
   * Used to build the {@code totalCount} field in the admin list response.
   */
  public long countAll() {
    if (session == null) return 0L;
    String query = "MATCH (p:Publication) RETURN count(p) AS n";
    var result = session.query(query, Map.of());
    var iter = result.iterator();
    if (!iter.hasNext()) return 0L;
    Object raw = iter.next().get("n");
    if (raw instanceof Number n) return n.longValue();
    return 0L;
  }

  /**
   * Find a Publication by its PID. Cheap path (O(1) via the
   * {@code @Index} on {@code pid}); used by
   * {@code /v2/.well-known/kip/{pid-suffix}}.
   */
  public Optional<Publication> findByPid(String pid) {
    if (pid == null || pid.isBlank()) return Optional.empty();
    String query = "MATCH (p:Publication {pid: $pid}) RETURN p LIMIT 1";
    Iterable<Publication> result = findByQuery(query, Map.of("pid", pid));
    var iter = result.iterator();
    if (!iter.hasNext()) return Optional.empty();
    return Optional.of(iter.next());
  }

  /**
   * KIP1h — return the highest {@code versionNumber} among all
   * {@code :Publication} rows attached to the entity, or {@code 0}
   * when the entity has no Publications yet.
   *
   * <p>Used by {@code PublishService} before minting: the next PID's
   * version is {@code findLatestVersionNumber(appId) + 1}. The query
   * uses {@code coalesce(max(p.versionNumber), 0)} so:
   * <ul>
   *   <li>fresh entities with zero Publications return {@code 0} (so
   *       the next mint is {@code v1}),</li>
   *   <li>existing entities with at least one Publication return
   *       the max version (so the next forced re-mint is one
   *       higher),</li>
   *   <li>and pre-KIP1h rows missing the {@code versionNumber}
   *       property are still counted via the V31 backfill (every
   *       legacy row has {@code versionNumber=1} after migration).</li>
   * </ul>
   */
  public int findLatestVersionNumber(String entityAppId) {
    if (entityAppId == null || entityAppId.isBlank()) return 0;
    if (session == null) return 0;
    String query =
      "MATCH (e {appId: $entityAppId})-[:" +
      HAS_PUBLICATION +
      "]->(p:Publication) " +
      "RETURN coalesce(max(p.versionNumber), 0) AS maxVersion";
    var result = session.query(query, Map.of("entityAppId", entityAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0;
    Object raw = iter.next().get("maxVersion");
    if (raw instanceof Number n) return n.intValue();
    return 0;
  }

  /**
   * Find every Publication attached to a given entity, ordered by
   * {@code mintedAt} descending (most-recent first — the
   * "current" Publication per the KIP1a append-only convention).
   */
  public List<Publication> findByEntityAppId(String entityAppId) {
    if (entityAppId == null || entityAppId.isBlank()) return List.of();
    String query =
      "MATCH (p:Publication) WHERE p.entityAppId = $entityAppId " +
      "RETURN p ORDER BY p.mintedAt DESC";
    Iterable<Publication> result = findByQuery(query, Map.of("entityAppId", entityAppId));
    List<Publication> out = new ArrayList<>();
    result.forEach(out::add);
    return out;
  }

  /**
   * KIP1f — set {@code digitalObjectMutability = 'retired'} on the
   * most-recent {@code :Publication} row for the given entity (by
   * {@code mintedAt DESC LIMIT 1}).
   *
   * <p>The row is never deleted — KIP records are append-only per the
   * HMC spec. The flag is a soft-state marker that signals "this
   * Publication is no longer the operator's intent" without destroying
   * the audit trail or breaking the PID resolver.
   *
   * <p>Idempotent: calling retire on an already-retired Publication
   * is a no-op ({@code SET} on the same property writes the same
   * value; Neo4j does not raise an error).
   *
   * @param entityAppId appId of the published entity
   * @return {@code true} when a row was found and updated (or was
   *         already retired); {@code false} when no {@code :Publication}
   *         exists for this entity (caller should return 404)
   */
  public boolean retireMostRecent(String entityAppId) {
    if (entityAppId == null || entityAppId.isBlank()) return false;
    if (session == null) return false;
    String query =
      "MATCH (p:Publication) WHERE p.entityAppId = $entityAppId " +
      "WITH p ORDER BY p.mintedAt DESC LIMIT 1 " +
      "SET p.digitalObjectMutability = 'retired' " +
      "RETURN count(p) AS n";
    var result = session.query(query, Map.of("entityAppId", entityAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return false;
    Object raw = iter.next().get("n");
    if (raw instanceof Number n) return n.longValue() > 0;
    return false;
  }

  /**
   * Persist the Publication and attach the
   * {@code (entity)-[:HAS_PUBLICATION]->(publication)} edge.
   *
   * <p>The OGM save is the {@code GenericDAO#createOrUpdate} path —
   * mints the {@code appId} on first save. The edge attachment is a
   * separate Cypher because the entity classes ({@code DataObject},
   * {@code Collection}) don't carry an OGM relationship to
   * {@code Publication} — KIP1a stays out of those frozen upstream
   * classes.
   *
   * @param publication freshly-built Publication to persist
   * @param entityAppId the appId of the entity to attach to
   * @return the saved Publication (with appId minted)
   */
  public Publication attachToEntity(Publication publication, String entityAppId) {
    if (publication == null) {
      throw new IllegalArgumentException("publication must not be null");
    }
    if (entityAppId == null || entityAppId.isBlank()) {
      throw new IllegalArgumentException("entityAppId must not be null/blank");
    }
    publication.setEntityAppId(entityAppId);
    Publication saved = createOrUpdate(publication);
    // Attach the edge. Idempotent via MERGE — re-saving the same
    // Publication won't create a duplicate edge.
    String query =
      "MATCH (e {appId: $entityAppId}), (p:Publication {appId: $pubAppId}) " +
      "MERGE (e)-[:" +
      HAS_PUBLICATION +
      "]->(p)";
    runQuery(query, Map.of("entityAppId", entityAppId, "pubAppId", saved.getAppId()));
    return saved;
  }
}
