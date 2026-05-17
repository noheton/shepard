package de.dlr.shepard.data.timeseries.sql;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.configuration.feature.toggles.SqlTimeseriesFeatureToggle;
import de.dlr.shepard.common.util.AccessType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.util.List;
import java.util.Set;

/**
 * P10a — {@code POST /v2/sql/timeseries}: JSON DSL bulk read endpoint for timeseries data.
 *
 * <p>Accepts a {@link SqlQuerySpec} body, resolves the caller's permission-gated container IDs
 * via {@link PermissionsService#filterAllowedForUser}, compiles the spec into a
 * {@link PreparedStatementSpec}, and streams results as JSON.
 *
 * <p>Gated on {@code shepard.timeseries.sql.enabled=false}; returns 404 when disabled.
 *
 * <p>See {@code aidocs/platform/29-p10-implementation-design.md §4} for the permission model
 * and {@code §11} for the P10a/P10b/P10c rollout plan.
 *
 * <p>Note (P10a): when the caller's {@code where.container_id_in} is empty, the permission
 * filter returns an empty set (see {@link PermissionsService#filterAllowedForUser} — it returns
 * {@code emptySet()} for empty input). This means an empty container list → 200 with empty rows.
 * P10b/c may revisit this to support "all readable containers" semantics.
 */
@Path("/v2/sql/timeseries")
@RequestScoped
public class SqlTimeseriesRest {

  /** Hard cap on the number of container IDs per request. Above this the IN-list planner cost
   *  overtakes a denormalised join — see {@code aidocs/29 §4}. */
  private static final int MAX_CONTAINERS = 1000;

  /** Default row cap when no {@code limit} is specified in the DSL. */
  private static final int DEFAULT_MAX_ROWS = 1_000_000;

  @Inject
  SqlQueryCompiler compiler;

  @Inject
  SqlQueryExecutor executor;

  @Inject
  PermissionsService permissionsService;

  /**
   * Execute a timeseries SQL DSL query and return matching rows as JSON.
   *
   * <p>Response shape: {@code {"rows": [...], "truncated": bool}}.
   *
   * @param spec            the JSON DSL request body
   * @param securityContext the JAX-RS security context
   * @return 200 with streaming JSON, 400 on bad DSL or too many containers, 404 if feature disabled
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response query(@Valid @NotNull SqlQuerySpec spec, @Context SecurityContext securityContext) {
    if (!SqlTimeseriesFeatureToggle.isActive()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    String username = securityContext.getUserPrincipal() != null
        ? securityContext.getUserPrincipal().getName()
        : null;

    List<Long> requestedIds = spec.where().containerIdIn() != null
        ? spec.where().containerIdIn()
        : List.of();

    Set<Long> allowed = permissionsService.filterAllowedForUser(requestedIds, AccessType.Read, username);

    if (allowed.size() > MAX_CONTAINERS) {
      throw new BadRequestException(
          ("Too many containers (%d); tighten the container_id_in filter (max %d)")
              .formatted(allowed.size(), MAX_CONTAINERS));
    }

    if (allowed.isEmpty()) {
      return Response.ok("{\"rows\":[],\"truncated\":false}")
          .type(MediaType.APPLICATION_JSON)
          .build();
    }

    PreparedStatementSpec compiled;
    try {
      compiled = compiler.compile(spec, allowed);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }

    int maxRows = spec.limit() != null
        ? Math.min(spec.limit(), DEFAULT_MAX_ROWS)
        : DEFAULT_MAX_ROWS;

    StreamingOutput stream = executor.executeJson(compiled, maxRows);
    return Response.ok(stream).type(MediaType.APPLICATION_JSON).build();
  }
}
