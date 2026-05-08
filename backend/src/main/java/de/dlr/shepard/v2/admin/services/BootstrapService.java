package de.dlr.shepard.v2.admin.services;

import de.dlr.shepard.auth.bootstrap.BootstrapState;
import de.dlr.shepard.auth.bootstrap.BootstrapStateDAO;
import de.dlr.shepard.auth.bootstrap.BootstrapTokenInitializer;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Service for {@code POST /v2/admin/bootstrap} — consumes the
 * one-shot bootstrap token (designed in {@code aidocs/51 §5.2}) and
 * grants {@code instance-admin} to the named user.
 *
 * <p>The bootstrap surface is unauthenticated by design — the token
 * IS the auth proof. Replay-protected: the {@code :BootstrapState}
 * node is deleted on consumption, so a second call returns 403.
 */
@RequestScoped
public class BootstrapService {

  @Inject
  BootstrapStateDAO bootstrapStateDAO;

  @Inject
  RoleDAO roleDAO;

  @Inject
  UserDAO userDAO;

  /**
   * Consume the bootstrap token + grant instance-admin to the named
   * user. Returns the username granted on success. Throws
   * {@link InvalidAuthException} if the token doesn't match (replay
   * or stale token) or if no bootstrap state exists.
   */
  public String consumeBootstrap(String token, String username) {
    if (token == null || token.isBlank()) {
      throw new InvalidRequestException("token is required");
    }
    if (username == null || username.isBlank()) {
      throw new InvalidRequestException("username is required");
    }
    Optional<BootstrapState> state = bootstrapStateDAO.findOne();
    if (state.isEmpty()) {
      throw new InvalidAuthException(
        "No bootstrap state on this shepard. Either an instance-admin already exists, " +
        "or the bootstrap token has already been consumed."
      );
    }
    String hash = BootstrapTokenInitializer.sha256Hex(token);
    if (!hash.equals(state.get().getTokenHash())) {
      Log.warn("BOOTSTRAP: token hash mismatch — refusing");
      throw new InvalidAuthException("Bootstrap token does not match");
    }

    var user = userDAO.find(username);
    if (user == null) {
      throw new InvalidPathException(
        "User '" + username + "' not found in Neo4j. The user must log in via OIDC at least once " +
        "before they can be bootstrapped, or be pre-created."
      );
    }

    roleDAO.ensureRole(Constants.INSTANCE_ADMIN_ROLE, Constants.INSTANCE_ADMIN_DISPLAY_NAME);
    long now = System.currentTimeMillis();
    boolean granted = roleDAO.grantRole(username, Constants.INSTANCE_ADMIN_ROLE, Constants.BOOTSTRAP_GRANTER, now);
    if (!granted) {
      throw new InvalidRequestException("Failed to grant instance-admin role");
    }

    // Consumption: delete the :BootstrapState — the next call will get a 403.
    bootstrapStateDAO.deleteAll();
    Log.infof("BOOTSTRAP: granted instance-admin to '%s' via bootstrap token", username);
    return username;
  }

  /**
   * @return true iff a bootstrap-state node exists. Surfaced by
   *         {@code GET /v2/admin/bootstrap/state} for tooling that
   *         wants to know if the deployment is in bootstrap mode.
   *         (Endpoint not shipped here; available via the service.)
   */
  public boolean hasOutstandingToken() {
    return bootstrapStateDAO.findOne().isPresent();
  }
}
