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
