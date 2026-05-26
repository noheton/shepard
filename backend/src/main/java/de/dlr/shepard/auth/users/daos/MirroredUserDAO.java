package de.dlr.shepard.auth.users.daos;

import de.dlr.shepard.auth.users.entities.MirroredUser;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;

/**
 * PROV-USER-MIRROR-ENDPOINT — DAO for {@link MirroredUser} nodes.
 *
 * <p>{@code @ApplicationScoped} — mirrors the {@link InstanceRorConfigDAO} pattern
 * used for all admin-config and cross-request-lifecycle nodes.
 *
 * <p><b>Idempotency.</b> {@link #findBySourceInstanceAndUsername} plus the
 * OGM {@code session.save()} call is the MERGE emulation pattern: look up first,
 * create only when absent, then save. This is the OGM-idiomatic approach and avoids
 * raw Cypher {@code MERGE} which would bypass OGM's relationship hydration.
 */
@ApplicationScoped
public class MirroredUserDAO extends GenericDAO<MirroredUser> {

  @Override
  public Class<MirroredUser> getEntityType() {
    return MirroredUser.class;
  }

  /**
   * Find a {@link MirroredUser} by its composite natural key
   * {@code (sourceInstance, sourceUsername)}.
   *
   * @param sourceInstance base URL of the source Shepard instance
   * @param sourceUsername username as known on the source instance
   * @return the matching node, or {@link Optional#empty()} if none exists yet
   */
  public Optional<MirroredUser> findBySourceInstanceAndUsername(String sourceInstance, String sourceUsername) {
    String query =
      "MATCH (n:MirroredUser) " +
      "WHERE n.sourceInstance = $sourceInstance AND n.sourceUsername = $sourceUsername " +
      "RETURN n";
    Map<String, Object> params = Map.of(
      "sourceInstance", sourceInstance,
      "sourceUsername", sourceUsername
    );
    Iterable<MirroredUser> results = findByQuery(query, params);
    for (MirroredUser u : results) {
      return Optional.of(u);
    }
    return Optional.empty();
  }

  /**
   * Idempotent create-or-update for a {@link MirroredUser} node.
   *
   * <p>If a node with the same {@code (sourceInstance, sourceUsername)} pair
   * already exists, its mutable fields ({@code sourceDisplayName},
   * {@code sourceEmail}) are updated in-place and the existing {@code appId} is
   * preserved. If no such node exists, a new one is created with a fresh UUID v7
   * {@code appId} minted by {@link AppIdGenerator#next()}.
   *
   * @param incoming the desired state; {@code appId} may be {@code null} — it will
   *                 be assigned on first save by {@link GenericDAO#createOrUpdate}
   * @return the saved (or updated) {@link MirroredUser} with a non-null {@code appId}
   */
  public MirroredUser createOrUpdateBySourceKey(MirroredUser incoming) {
    Optional<MirroredUser> existing = findBySourceInstanceAndUsername(
      incoming.getSourceInstance(),
      incoming.getSourceUsername()
    );

    if (existing.isPresent()) {
      MirroredUser current = existing.get();
      Log.debugf(
        "MirroredUser MERGE — existing node found (appId=%s), updating display name and email",
        current.getAppId()
      );
      // Preserve the existing appId and id — only update the mutable fields.
      current.setSourceDisplayName(incoming.getSourceDisplayName());
      current.setSourceEmail(incoming.getSourceEmail());
      // derivedFrom is intentionally not overwritten on update (chaining is set once at creation).
      return createOrUpdate(current);
    }

    Log.debugf(
      "MirroredUser MERGE — no existing node for (%s, %s), creating new",
      incoming.getSourceInstance(),
      incoming.getSourceUsername()
    );
    return createOrUpdate(incoming);
  }
}
