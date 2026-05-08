package de.dlr.shepard.auth.role.daos;

import de.dlr.shepard.auth.role.entities.Role;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DAO for the {@link Role} node entity and the
 * {@code (:User)-[:HAS_ROLE]->(:Role)} relationship.
 *
 * <p>The {@code :HAS_ROLE} relationship carries audit properties
 * ({@code grantedBy}, {@code grantedAt}) which are not modelled on
 * {@code Role} itself, so role grants are queried via direct Cypher
 * here rather than relying on Neo4j-OGM's relationship-entity
 * mechanism.
 */
@RequestScoped
public class RoleDAO extends GenericDAO<Role> {

  /** Find a role by its hyphenated identifier (e.g. {@code "instance-admin"}). */
  public Optional<Role> findByName(String name) {
    var iter = findByQuery("MATCH (r:Role {name: $name}) RETURN r LIMIT 1", Map.of("name", name));
    var it = iter.iterator();
    if (!it.hasNext()) return Optional.empty();
    return Optional.of(it.next());
  }

  /**
   * Ensure a {@link Role} node exists for the given name; create it
   * with the supplied display name if missing. Idempotent.
   */
  public Role ensureRole(String name, String displayName) {
    return findByName(name).orElseGet(() -> createOrUpdate(new Role(name, displayName)));
  }

  /**
   * Returns true iff the user has a {@code :HAS_ROLE} edge to the
   * {@code Role {name}} node.
   */
  public boolean hasRole(String username, String roleName) {
    String cypher = "MATCH (u:User {username: $u})-[:HAS_ROLE]->(r:Role {name: $n}) RETURN count(r) > 0 AS has";
    var result = session.query(cypher, Map.of("u", username, "n", roleName));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return false;
    Object has = it.next().get("has");
    return Boolean.TRUE.equals(has);
  }

  /**
   * Create the {@code :HAS_ROLE} edge from the named user to the named
   * role, recording {@code grantedBy} / {@code grantedAt}. Idempotent
   * via {@code MERGE}; re-granting an existing role updates the
   * audit timestamp/grantor.
   */
  public boolean grantRole(String username, String roleName, String grantedBy, long grantedAtMillis) {
    Map<String, Object> params = new HashMap<>();
    params.put("u", username);
    params.put("n", roleName);
    params.put("g", grantedBy);
    params.put("ts", grantedAtMillis);
    String cypher =
      "MATCH (u:User {username: $u}), (r:Role {name: $n}) " +
      "MERGE (u)-[h:HAS_ROLE]->(r) " +
      "SET h.grantedBy = $g, h.grantedAt = $ts " +
      "RETURN count(h) > 0 AS ok";
    var result = session.query(cypher, params);
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return false;
    return Boolean.TRUE.equals(it.next().get("ok"));
  }

  /**
   * Delete the {@code :HAS_ROLE} edge from the named user to the
   * named role. Returns true iff an edge was actually deleted.
   */
  public boolean revokeRole(String username, String roleName) {
    String cypher =
      "MATCH (:User {username: $u})-[h:HAS_ROLE]->(:Role {name: $n}) " +
      "WITH h, count(h) AS c DELETE h RETURN c";
    var result = session.query(cypher, Map.of("u", username, "n", roleName));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return false;
    Object c = it.next().get("c");
    return c instanceof Number n && n.longValue() > 0;
  }

  /**
   * List all role grants for the given role, returning audit metadata
   * (granted by, granted at, ms-since-epoch). Returns an empty list if
   * the role doesn't exist.
   */
  public List<RoleGrant> listGrants(String roleName) {
    String cypher =
      "MATCH (u:User)-[h:HAS_ROLE]->(:Role {name: $n}) " +
      "RETURN u.username AS username, h.grantedBy AS grantedBy, h.grantedAt AS grantedAt " +
      "ORDER BY u.username";
    var result = session.query(cypher, Map.of("n", roleName));
    List<RoleGrant> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      out.add(
        new RoleGrant(
          Objects.toString(row.get("username"), null),
          Objects.toString(row.get("grantedBy"), null),
          row.get("grantedAt") instanceof Number n ? n.longValue() : null
        )
      );
    }
    return out;
  }

  /**
   * Returns the count of users who hold the given role via a
   * {@code :HAS_ROLE} edge (Neo4j-internal source only — does NOT
   * include IdP-claim grants).
   */
  public long countGrants(String roleName) {
    String cypher = "MATCH (:User)-[:HAS_ROLE]->(:Role {name: $n}) RETURN count(*) AS c";
    var result = session.query(cypher, Map.of("n", roleName));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * Map of role-grant audit metadata keyed by the role-name. Used by
   * {@link #listGrantsForUser(String)} so a user with multiple roles
   * (a future thing — v1 has only {@code instance-admin}) returns
   * one row per role.
   */
  public Map<String, RoleGrant> listGrantsForUser(String username) {
    String cypher =
      "MATCH (u:User {username: $u})-[h:HAS_ROLE]->(r:Role) " +
      "RETURN r.name AS name, h.grantedBy AS grantedBy, h.grantedAt AS grantedAt";
    var result = session.query(cypher, Map.of("u", username));
    Map<String, RoleGrant> out = new LinkedHashMap<>();
    for (Map<String, Object> row : result.queryResults()) {
      String name = Objects.toString(row.get("name"), null);
      if (name == null) continue;
      out.put(
        name,
        new RoleGrant(
          username,
          Objects.toString(row.get("grantedBy"), null),
          row.get("grantedAt") instanceof Number n ? n.longValue() : null
        )
      );
    }
    return out;
  }

  /**
   * Returns the set of role-names the user holds via a {@code :HAS_ROLE}
   * edge in Neo4j. Cheap fast-path used on every authenticated request
   * by the JWT filter.
   */
  public List<String> rolesForUser(String username) {
    String cypher = "MATCH (:User {username: $u})-[:HAS_ROLE]->(r:Role) RETURN r.name AS name";
    var result = session.query(cypher, Map.of("u", username));
    List<String> out = new ArrayList<>();
    for (Map<String, Object> row : result.queryResults()) {
      Object name = row.get("name");
      if (name != null) out.add(name.toString());
    }
    return out;
  }

  @Override
  public Class<Role> getEntityType() {
    return Role.class;
  }

  /**
   * Audit-trail record for a single role grant — captures which user
   * holds the role, who granted it, and when (millis since epoch, may
   * be {@code null} for grants pre-dating this property).
   */
  public record RoleGrant(String username, String grantedBy, Long grantedAtMillis) {}
}
