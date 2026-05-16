package de.dlr.shepard.v2.admin.hdf;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import de.dlr.shepard.v2.admin.hdf.io.HdfRebuildAclsResultIO;
import io.quarkus.logging.Log;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * A5b Phase 2 — operator escape-hatch for HSDS ACL drift recovery.
 *
 * <p>Iterates every {@code :HdfContainer} in Neo4j, derives the
 * expected HSDS ACL from shepard's permission graph, and rewrites
 * the HSDS domain ACL from scratch. Idempotent — safe to run any
 * time. Use cases:
 * <ul>
 *   <li>Operator panic button: shepard and HSDS look out of sync;
 *       this re-asserts shepard's view.</li>
 *   <li>After an HSDS data migration / restore from backup.</li>
 *   <li>After flipping {@code shepard.hdf.enabled} on for the first
 *       time on a populated database.</li>
 * </ul>
 *
 * <p>The endpoint is gated on {@code shepard.hdf.enabled=true} via
 * {@link EndpointDisabled} — when the toggle is off, the endpoint
 * returns 404 (the same shape as every other {@code /v2/hdf-*}
 * endpoint).
 *
 * <p>Returns {@link HdfRebuildAclsResultIO} on success — partial
 * failures live in the {@code errors[]} array with 200 OK overall.
 * RFC 7807 {@link ProblemJson} is returned only when the request
 * can't proceed at all (feature off in a context where the toggle
 * wasn't caught at the endpoint level, or another non-iterable
 * error). See {@code aidocs/35 §6}.
 */
@EndpointDisabled(name = "shepard.hdf.enabled", stringValue = "false")
@Path("/v2/admin/hdf")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class HdfAdminRest {

  /** RFC 7807 type-URI for the "feature off" failure. */
  static final String PROBLEM_TYPE_DISABLED = "/problems/hdf.disabled";

  /** RFC 7807 type-URI for partial-failure across containers. */
  static final String PROBLEM_TYPE_PARTIAL_FAILURE = "/problems/hdf.bridge.partial-failure";

  @Inject
  HdfContainerDAO hdfContainerDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  Instance<HsdsClient> hsdsClientInstance;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Defence-in-depth role check: in addition to {@code @RolesAllowed},
   * we re-verify against the {@link SecurityContext}. Mirrors the
   * pattern in {@code InstanceAdminRest#requireInstanceAdmin}.
   */
  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || securityContext.getUserPrincipal() == null) {
      throw new InvalidAuthException("Authentication required");
    }
    if (!securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  @POST
  @Path("/rebuild-acls")
  @Operation(
    summary = "Rebuild every HSDS domain ACL from shepard's permission graph.",
    description = "Iterates every :HdfContainer in Neo4j, derives the expected HSDS ACL from " +
    "shepard's Permissions graph, and rewrites the HSDS domain ACL from scratch. " +
    "Idempotent — safe to re-run. Use when HSDS ACLs have drifted (e.g. after a backup " +
    "restore, or after flipping shepard.hdf.enabled on for the first time on a populated DB)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Rebuild attempted. Partial failures land in errors[] with 200 OK overall.",
    content = @Content(schema = @Schema(implementation = HdfRebuildAclsResultIO.class))
  )
  @APIResponse(
    responseCode = "401",
    description = "Authentication required (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "403",
    description = "Caller lacks the instance-admin role (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "404",
    description = "shepard.hdf.enabled=false (endpoint disabled)."
  )
  @APIResponse(
    responseCode = "503",
    description = "HSDS feature off (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  public Response rebuildAcls(@Context SecurityContext securityContext) {
    try {
      requireInstanceAdmin(securityContext);
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(
        "/problems/auth.denied",
        denied.getMessage(),
        status,
        denied.getMessage()
      );
    }

    if (hsdsClientInstance == null || hsdsClientInstance.isUnsatisfied()) {
      Log.warn("HSDS rebuild-acls invoked while feature is off");
      return problem(
        PROBLEM_TYPE_DISABLED,
        "HDF feature is disabled",
        Status.SERVICE_UNAVAILABLE,
        "shepard.hdf.enabled=false — set the toggle on and supply HSDS credentials before invoking rebuild-acls."
      );
    }
    HsdsClient hsds = hsdsClientInstance.get();

    Collection<HdfContainer> all;
    try {
      all = hdfContainerDAO.findAll();
    } catch (RuntimeException e) {
      Log.errorf(e, "rebuild-acls: failed to enumerate HdfContainers");
      return problem(
        PROBLEM_TYPE_PARTIAL_FAILURE,
        "Failed to enumerate HdfContainer rows",
        Status.INTERNAL_SERVER_ERROR,
        e.getMessage()
      );
    }

    int processed = 0;
    int synced = 0;
    List<HdfRebuildAclsResultIO.Error> errors = new ArrayList<>();

    for (HdfContainer container : all) {
      if (container == null) continue;
      if (container.isDeleted()) continue;
      processed++;
      String appId = container.getAppId();
      String domain = container.getHsdsDomain();
      if (domain == null || domain.isBlank()) {
        errors.add(new HdfRebuildAclsResultIO.Error(appId, "container has no hsdsDomain — provisioning incomplete"));
        continue;
      }
      Optional<Permissions> permsOpt = permissionsService.getPermissionsOfEntityOptional(container.getId());
      if (permsOpt.isEmpty()) {
        // No permissions row — fail-closed: clear all non-owner ACEs.
        try {
          hsds.clearDomainAcl(domain);
          synced++;
        } catch (RuntimeException e) {
          errors.add(new HdfRebuildAclsResultIO.Error(appId, "clearDomainAcl failed: " + safeMessage(e)));
        }
        continue;
      }
      Permissions perms = permsOpt.get();
      String owner = perms.getOwner() == null ? null : perms.getOwner().getUsername();
      Set<String> readers = flatten(perms.getReader(), expand(perms.getReaderGroups()));
      Set<String> writers = flatten(perms.getWriter(), expand(perms.getWriterGroups()));
      Set<String> managers = flatten(perms.getManager(), List.of());

      try {
        hsds.setDomainAcl(domain, owner, readers, writers, managers);
        synced++;
      } catch (RuntimeException e) {
        errors.add(new HdfRebuildAclsResultIO.Error(appId, "setDomainAcl failed: " + safeMessage(e)));
      }
    }

    HdfRebuildAclsResultIO result = new HdfRebuildAclsResultIO(processed, synced, errors);
    Log.infof("HSDS rebuild-acls completed: processed=%d synced=%d errors=%d", processed, synced, errors.size());
    return Response.ok(result).build();
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  private static String safeMessage(Throwable t) {
    if (t == null) return "<unknown>";
    String m = t.getMessage();
    if (m == null) return t.getClass().getSimpleName();
    return m.length() > 300 ? m.substring(0, 300) + "…" : m;
  }

  private static Set<String> flatten(List<User> users, List<User> extras) {
    Set<String> out = new LinkedHashSet<>();
    if (users != null) {
      for (User u : users) {
        if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
          out.add(u.getUsername());
        }
      }
    }
    if (extras != null) {
      for (User u : extras) {
        if (u != null && u.getUsername() != null && !u.getUsername().isBlank()) {
          out.add(u.getUsername());
        }
      }
    }
    return out;
  }

  private static List<User> expand(List<UserGroup> groups) {
    if (groups == null || groups.isEmpty()) return List.of();
    List<User> out = new ArrayList<>();
    for (UserGroup g : groups) {
      if (g == null) continue;
      List<User> members = g.getUsers();
      if (members == null) continue;
      for (User u : members) {
        if (u != null) out.add(u);
      }
    }
    return out;
  }
}
