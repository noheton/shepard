package de.dlr.shepard.v2.provenance.resources;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.output.OutputProfile;
import de.dlr.shepard.common.output.OutputProfileResolver;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.provenance.io.ActivityIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /v2/provenance/...} read endpoints per
 * {@code aidocs/55 §6}. v1 ships the flat list-activities query;
 * per-entity drill-down and pre-aggregated stats land in later
 * PROV1 slices.
 *
 * <p>Every authenticated user can read their own activities. Reading
 * activities for **other** users is gated to
 * {@code instance-admin} per {@code aidocs/51}: the casual user sees
 * their personal trail; the operator sees the instance.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/provenance")
@RequestScoped
@Tag(name = "Provenance (v2)")
public class ProvenanceRest {

  @Inject
  ProvenanceService provenance;

  @Inject
  AuthenticationContext authContext;

  @Inject
  OutputProfileResolver outputProfile;

  @GET
  @Path("/activities")
  @Operation(
    summary = "List provenance activities (most recent first).",
    description = "Filterable by agent / target / time window. Casual users see only their own " +
    "rows; instance-admins see all. Caps at 1000 rows per response — paginate via " +
    "narrowing the time window."
  )
  @APIResponse(
    responseCode = "200",
    description = "Matching activities, sorted by startedAt DESC.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ActivityIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller asked for another user's rows without instance-admin role.")
  public Response listActivities(
    @Parameter(description = "Filter to a specific Agent's activities. Casual users may only pass their own username.")
    @QueryParam("agent") String agent,
    @Parameter(description = "Filter to a specific target-entity kind, e.g. 'Collection' or 'DataObject'.")
    @QueryParam("targetKind") String targetKind,
    @Parameter(description = "Filter to a specific target-entity appId.") @QueryParam("targetAppId") String targetAppId,
    @Parameter(description = "Inclusive lower bound on startedAt (millis since epoch).") @QueryParam("since") Long since,
    @Parameter(description = "Inclusive upper bound on startedAt (millis since epoch).") @QueryParam("until") Long until,
    @Parameter(description = "Max rows. Defaults to 100; capped at 1000.") @QueryParam("limit") Integer limit,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean isAdmin = securityContext.isUserInRole("instance-admin");
    // Casual users can only see their own activity rows. Asking for someone
    // else's without the admin role → 403.
    if (agent == null) {
      // Default: caller sees their own rows. Admins see everyone unless they
      // narrow with ?agent=.
      if (!isAdmin) agent = caller;
    } else if (!agent.equals(caller) && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    int eff = limit == null ? 100 : limit;
    OutputProfile prof = outputProfile.getProfile();
    List<ActivityIO> rows = provenance
      .list(agent, targetKind, targetAppId, since, until, eff)
      .stream()
      .map(ActivityIO::from)
      .map(io -> applyProfile(io, prof))
      .toList();
    return Response.ok(rows).build();
  }

  private static ActivityIO applyProfile(ActivityIO io, OutputProfile profile) {
    return switch (profile) {
      case METADATA -> io.metadataOnly();
      case RELATIONS -> io.relationsOnly();
      case ALL -> io;
    };
  }

  @GET
  @Path("/count")
  @Operation(
    summary = "Count provenance activities matching the same filter set.",
    description = "Cheap variant of /activities that returns only the row count, for dashboard tiles."
  )
  @APIResponse(responseCode = "200", description = "Row count.")
  public Response countActivities(
    @QueryParam("agent") String agent,
    @QueryParam("targetKind") String targetKind,
    @QueryParam("targetAppId") String targetAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean isAdmin = securityContext.isUserInRole("instance-admin");
    if (agent == null) {
      if (!isAdmin) agent = caller;
    } else if (!agent.equals(caller) && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    long c = provenance.count(agent, targetKind, targetAppId, since, until);
    return Response.ok(java.util.Map.of("count", c)).build();
  }
}
