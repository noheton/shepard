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
