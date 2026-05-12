package de.dlr.shepard.auth.users.daos;

import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * CRUD for {@link GitCredential} nodes scoped to the owning user.
 *
 * <p>All queries traverse the {@code OWNS_CREDENTIAL} relationship so a
 * user can only ever see and mutate their own credentials — the ownership
 * check is baked into the Cypher, not left to the service layer.
 */
@RequestScoped
public class GitCredentialDAO extends GenericDAO<GitCredential> {

  /**
   * @param username  the owning user's username (Neo4j @Id on User).
   * @param credAppId the credential's appId.
   * @return the credential, or {@code null} if not found / not owned by
   *         this user.
   */
  public GitCredential findByUserAndAppId(String username, String credAppId) {
    String query =
      "MATCH (u:User {username: $username})-[:OWNS_CREDENTIAL]->(c:GitCredential {appId: $appId}) RETURN c";
    var iter = findByQuery(query, Map.of("username", username, "appId", credAppId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * @param username the owning user's username.
   * @return all credentials owned by this user, ordered by creation date
   *         ascending (nulls last).
   */
  public List<GitCredential> findAllByUser(String username) {
    String query =
      "MATCH (u:User {username: $username})-[:OWNS_CREDENTIAL]->(c:GitCredential) RETURN c ORDER BY c.createdAt ASC";
    var iter = findByQuery(query, Map.of("username", username));
    return StreamSupport.stream(iter.spliterator(), false).toList();
  }

  /**
   * Creates the {@code GitCredential} node and wires the
   * {@code OWNS_CREDENTIAL} relationship from the owning user in a single
   * Cypher statement. Mints {@code appId} (UUID v7) and sets
   * {@code createdAt} here so callers don't need to touch those fields.
   *
   * @param username   the owning user's username.
   * @param credential the credential to persist (modified in place).
   * @return the same credential instance after persistence.
   */
  public GitCredential createForUser(String username, GitCredential credential) {
    credential.setAppId(AppIdGenerator.next());
    credential.setCreatedAt(new Date());

    String query =
      "MATCH (u:User {username: $username}) " +
      "CREATE (u)-[:OWNS_CREDENTIAL]->(c:GitCredential {" +
      "  appId: $appId, host: $host, displayName: $displayName, " +
      "  username: $credUsername, encryptedPat: $encryptedPat, createdAt: $createdAt" +
      "}) RETURN c";

    Map<String, Object> params = Map.of(
      "username", username,
      "appId", credential.getAppId(),
      "host", credential.getHost() != null ? credential.getHost() : "",
      "displayName", credential.getDisplayName() != null ? credential.getDisplayName() : "",
      "credUsername", credential.getUsername() != null ? credential.getUsername() : "",
      "encryptedPat", credential.getEncryptedPat() != null ? credential.getEncryptedPat() : "",
      "createdAt", credential.getCreatedAt().getTime()
    );

    runQuery(query, params);
    return credential;
  }

  /**
   * Updates a credential's mutable fields in place. Only fields that are
   * non-null on {@code updated} are applied; null means "leave unchanged".
   * The caller is responsible for re-encrypting the PAT before passing it
   * here.
   *
   * @param username   the owning user's username (ownership guard).
   * @param credAppId  the credential to update.
   * @param updated    carrier for the changed fields.
   * @return the reloaded credential after the update, or {@code null} if
   *         not found.
   */
  public GitCredential updateByUserAndAppId(String username, String credAppId, GitCredential updated) {
    StringBuilder setClauses = new StringBuilder();
    java.util.HashMap<String, Object> params = new java.util.HashMap<>();
    params.put("username", username);
    params.put("appId", credAppId);

    if (updated.getDisplayName() != null) {
      setClauses.append(" SET c.displayName = $displayName");
      params.put("displayName", updated.getDisplayName());
    }
    if (updated.getUsername() != null) {
      setClauses.append(" SET c.username = $credUsername");
      params.put("credUsername", updated.getUsername());
    }
    if (updated.getEncryptedPat() != null) {
      setClauses.append(" SET c.encryptedPat = $encryptedPat");
      params.put("encryptedPat", updated.getEncryptedPat());
    }

    if (setClauses.isEmpty()) {
      // nothing to update — reload and return
      return findByUserAndAppId(username, credAppId);
    }

    String query =
      "MATCH (u:User {username: $username})-[:OWNS_CREDENTIAL]->(c:GitCredential {appId: $appId})" +
      setClauses +
      " RETURN c";

    runQuery(query, params);
    return findByUserAndAppId(username, credAppId);
  }

  /**
   * Detaches and deletes the credential. Only removes the node if the
   * owning user matches.
   *
   * @param username  the owning user's username.
   * @param credAppId the credential's appId.
   * @return {@code true} if a node was deleted, {@code false} if not found.
   */
  public boolean deleteByUserAndAppId(String username, String credAppId) {
    String query =
      "MATCH (u:User {username: $username})-[:OWNS_CREDENTIAL]->(c:GitCredential {appId: $appId}) DETACH DELETE c";
    return runQuery(query, Map.of("username", username, "appId", credAppId));
  }

  @Override
  public Class<GitCredential> getEntityType() {
    return GitCredential.class;
  }
}
