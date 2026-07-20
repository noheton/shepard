package de.dlr.shepard.auth.users.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collection;
import java.util.Optional;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

@RequestScoped
public class UserDAO extends GenericDAO<User> {

  public User find(String username) {
    User entity = session.load(getEntityType(), username, DEPTH_ENTITY);
    return entity;
  }

  /**
   * Find a user by username loading only the node itself (depth 0) — no mapped
   * relationships (subscriptions / apiKeys / gitCredentials).
   *
   * <p>NEO-AUDIT-2026-07-20-USER-SUPERNODE: the {@code DEPTH_ENTITY=1} {@link #find}
   * generates an OGM {@code MATCH p=(n)-[*0..1]-(m)} that drags <em>every</em>
   * relationship on the node over the wire — including the millions of
   * <em>unmapped</em> incoming {@code WAS_ASSOCIATED_WITH} provenance edges (and
   * their Activity nodes) on the shared service {@code :User}. On a 2.87M-degree
   * user that is a multi-second, multi-hundred-MB load that OGM then mostly
   * discards, and it is on the hot path of every authenticated mutation
   * (each create does {@code setCreatedBy(getCurrentUser())}). Depth 0 returns the
   * node with its scalar properties — crucially {@code username}, {@code appId} and
   * the role {@code @Property} fields — which is all an identity-only caller needs.
   *
   * <p>Use only where the returned user is consumed for identity / role checks or a
   * {@code setCreatedBy}/{@code setUpdatedBy} edge — never where the caller reads the
   * mapped collections (those need {@link #find}).
   *
   * @param username the OGM {@code @Id} username to load
   * @return the User node at depth 0, or {@code null} when none exists
   */
  public User findLight(String username) {
    return session.load(getEntityType(), username, 0);
  }

  /**
   * Find a user by their email address.
   *
   * <p>After the V81 {@code user_email_unique} constraint is applied, at most one
   * :User node can share a given email. This method returns that node (or empty
   * if none exists). Pre-migration callers should treat an unexpected multi-hit
   * result as a signal that V81 has not yet run — the startup WARN in
   * {@link de.dlr.shepard.auth.users.services.UserService} surfaces this case.
   *
   * @param email the email address to look up (case-sensitive)
   * @return an Optional containing the matching User, or empty when none found
   */
  public Optional<User> findByEmail(String email) {
    if (email == null || email.isBlank()) return Optional.empty();
    Filter f = new Filter("email", ComparisonOperator.EQUALS, email);
    Collection<User> hits = session.loadAll(getEntityType(), f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return Optional.empty();
    return Optional.of(hits.iterator().next());
  }

  /**
   * Count the number of distinct email addresses that appear on more than one
   * :User node. Returns 0 once the V81 uniqueness constraint is active (it is
   * impossible to insert duplicates at that point). Used by the startup WARN
   * in {@link de.dlr.shepard.auth.users.services.UserService}.
   *
   * @return number of email values shared by two or more :User nodes
   */
  public long countDuplicateEmails() {
    var result = session.query(
      "MATCH (u:User) WHERE u.email IS NOT NULL AND u.email <> '' " +
      "WITH u.email AS email, count(*) AS c WHERE c > 1 RETURN count(email) AS n",
      java.util.Map.of()
    );
    var row = result.iterator();
    if (!row.hasNext()) return 0L;
    Object val = row.next().get("n");
    return val instanceof Number n ? n.longValue() : 0L;
  }

  public boolean delete(String username) {
    User entity = session.load(getEntityType(), username);
    if (entity != null) {
      session.delete(entity);
      return true;
    }
    return false;
  }

  @Override
  public Class<User> getEntityType() {
    return User.class;
  }
}
