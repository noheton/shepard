package de.dlr.shepard.v2.admin.services;

import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.InstanceAdminGrantIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service for the {@code /v2/admin/instance-admins} endpoints —
 * lists, grants, revokes the {@code instance-admin} role.
 *
 * <p>Two grant sources surface:
 * <ul>
 *   <li>{@code Neo4j} — the {@code :HAS_ROLE} edge in shepard's
 *       Neo4j (the source this service mutates)
 *   <li>{@code IdP} — the role-string in the JWT claim mapped to
 *       {@code instance-admin}; see {@code aidocs/51 §3.3}. We can't
 *       enumerate IdP-side grants from inside shepard (the IdP is the
 *       source of truth; we'd need its admin API), so the {@code IdP}
 *       column is empty here. The CLI's table-render and {@code /me}
 *       page surface the IdP-source on a per-user basis from each
 *       user's own JWT.
 * </ul>
 */
@RequestScoped
public class InstanceAdminService {

  @Inject
  RoleDAO roleDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  @ConfigProperty(name = "shepard.instance-admin.role")
  java.util.Optional<String> instanceAdminClaimValueOpt;

  /**
   * List every Neo4j-side instance-admin grant. Per the source-column
   * caveat above, only the Neo4j source is enumerated — the IdP
   * source is implicit and per-request.
   */
  public List<InstanceAdminGrantIO> listInstanceAdmins() {
    List<RoleDAO.RoleGrant> grants = roleDAO.listGrants(Constants.INSTANCE_ADMIN_ROLE);
    List<InstanceAdminGrantIO> out = new ArrayList<>(grants.size());
    for (var g : grants) {
      out.add(
        new InstanceAdminGrantIO(
          g.username(),
          "Neo4j",
          g.grantedBy(),
          g.grantedAtMillis() == null ? null : new Date(g.grantedAtMillis())
        )
      );
    }
    return out;
  }

  /**
   * Grant the {@code instance-admin} role to the named user. The user
   * must exist in Neo4j (i.e. they must have logged in at least once
   * via OIDC, or been pre-created). The grantor is the calling
   * username.
   */
  public InstanceAdminGrantIO grantInstanceAdmin(String username, String grantedBy) {
    if (username == null || username.isBlank()) {
      throw new InvalidRequestException("username must be non-blank");
    }
    var user = userDAO.find(username);
    if (user == null) {
      throw new InvalidPathException("User '" + username + "' not found in Neo4j");
    }
    roleDAO.ensureRole(Constants.INSTANCE_ADMIN_ROLE, Constants.INSTANCE_ADMIN_DISPLAY_NAME);
    long now = System.currentTimeMillis();
    boolean granted = roleDAO.grantRole(username, Constants.INSTANCE_ADMIN_ROLE, grantedBy, now);
    if (!granted) {
      throw new InvalidRequestException("Failed to grant instance-admin role");
    }
    Log.infof("Granted instance-admin role to '%s' (grantedBy=%s)", username, grantedBy);
    return new InstanceAdminGrantIO(username, "Neo4j", grantedBy, new Date(now));
  }

  /**
   * Revoke the {@code instance-admin} role from the named user.
   * Returns {@code true} iff a Neo4j-side edge was actually deleted.
   */
  public boolean revokeInstanceAdmin(String username) {
    if (username == null || username.isBlank()) {
      throw new InvalidRequestException("username must be non-blank");
    }
    boolean revoked = roleDAO.revokeRole(username, Constants.INSTANCE_ADMIN_ROLE);
    if (revoked) {
      Log.infof("Revoked instance-admin role from '%s'", username);
    }
    return revoked;
  }

  /**
   * Returns the set of role-strings the calling user holds, derived
   * from the Neo4j edge plus the dual-source IdP claim if applicable.
   * Used by the {@code /me} surface (post-U1c).
   */
  public Set<String> rolesForUser(String username, Iterable<String> idpClaimRoles) {
    Set<String> out = new LinkedHashSet<>();
    String instanceAdminClaimValue = instanceAdminClaimValueOpt == null
      ? ""
      : instanceAdminClaimValueOpt.map(String::trim).orElse("");
    if (idpClaimRoles != null && !instanceAdminClaimValue.isBlank()) {
      for (String r : idpClaimRoles) {
        if (instanceAdminClaimValue.equals(r)) {
          out.add(Constants.INSTANCE_ADMIN_ROLE);
          break;
        }
      }
    }
    if (roleDAO != null) {
      out.addAll(roleDAO.rolesForUser(username));
    }
    return out;
  }
}
