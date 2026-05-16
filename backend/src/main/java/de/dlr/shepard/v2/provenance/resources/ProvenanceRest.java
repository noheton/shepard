package de.dlr.shepard.v2.provenance.resources;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.output.OutputProfile;
import de.dlr.shepard.common.output.OutputProfileResolver;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import de.dlr.shepard.provenance.services.ProvJsonRenderer;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.provenance.services.ProvenanceStatsService;
import de.dlr.shepard.v2.provenance.io.ActivityIO;
import de.dlr.shepard.v2.provenance.io.ProvenanceStatsIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
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
  de.dlr.shepard.auth.permission.services.PermissionsService permissionsService;

  @Inject
  de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  OutputProfileResolver outputProfile;

  @Inject
  ProvJsonRenderer provJsonRenderer;

  @Inject
  ProvJsonLdRenderer provJsonLdRenderer;

  @Inject
  ProvenanceStatsService statsService;

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
  @Path("/activities")
  @Produces(ProvJsonRenderer.MEDIA_TYPE)
  @Operation(
    summary = "List provenance activities as W3C PROV-JSON (most recent first).",
    description = "Same query semantics as the JSON variant; output shape conforms to a small subset " +
    "of the W3C PROV-JSON Submission (activity / agent / entity / wasAssociatedWith / used / " +
    "wasGeneratedBy blocks). Triggered by Accept: application/prov+json. Honours ?profile= " +
    "for filtering, though the PROV-JSON serialisation always emits the full PROV-O fields."
  )
  @APIResponse(responseCode = "200", description = "Activities serialised as PROV-JSON.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller asked for another user's rows without instance-admin role.")
  public Response listActivitiesProvJson(
    @QueryParam("agent") String agent,
    @QueryParam("targetKind") String targetKind,
    @QueryParam("targetAppId") String targetAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @QueryParam("limit") Integer limit,
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

    int eff = limit == null ? 100 : limit;
    List<Activity> rows = provenance.list(agent, targetKind, targetAppId, since, until, eff);
    return Response.ok(provJsonRenderer.render(rows)).type(ProvJsonRenderer.MEDIA_TYPE).build();
  }

  @GET
  @Path("/activities")
  @Produces(ProvJsonLdRenderer.MEDIA_TYPE)
  @Operation(
    summary = "List provenance activities as JSON-LD (PROV-O default; metadata4ing profile opt-in).",
    description = "Same query semantics as the plain-JSON variant. Triggered by " +
    "Accept: application/ld+json. Pass profile=\"https://w3id.org/nfdi4ing/metadata4ing/\" " +
    "(or short form profile=metadata4ing) on the Accept header to receive the metadata4ing " +
    "flavour — m4i:ProcessingStep / m4i:InvestigatedObject / m4i:Person nodes on top of the " +
    "PROV-O parent types. Unknown profile → 406 RFC 7807 provenance.unsupported-profile."
  )
  @APIResponse(responseCode = "200", description = "Activities serialised as JSON-LD.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller asked for another user's rows without instance-admin role.")
  @APIResponse(responseCode = "406", description = "Unknown profile= parameter on the Accept header.")
  public Response listActivitiesJsonLd(
    @QueryParam("agent") String agent,
    @QueryParam("targetKind") String targetKind,
    @QueryParam("targetAppId") String targetAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @QueryParam("limit") Integer limit,
    @HeaderParam(HttpHeaders.ACCEPT) String acceptHeader,
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

    Response profileError = enforceJsonLdProfile(acceptHeader);
    if (profileError != null) return profileError;
    ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);

    int eff = limit == null ? 100 : limit;
    List<Activity> rows = provenance.list(agent, targetKind, targetAppId, since, until, eff);
    return Response.ok(provJsonLdRenderer.render(rows, profile))
      .type(jsonLdMediaTypeFor(profile))
      .build();
  }

  @GET
  @Path("/entity/{appId}")
  @Operation(
    summary = "Provenance trail for a single entity (most recent first).",
    description = "Returns every captured Activity whose targetAppId matches the supplied entity. " +
    "Casual users see only rows whose acting Agent is themselves; instance-admins see all rows " +
    "targeting the entity. Honours ?profile=metadata|relations|all from V2S1a. Caps at 1000 rows."
  )
  @APIResponse(
    responseCode = "200",
    description = "Activities targeting the entity, sorted by startedAt DESC.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ActivityIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listEntityActivities(
    @Parameter(description = "Target entity's appId.", required = true) @PathParam("appId") String entityAppId,
    @Parameter(description = "Inclusive lower bound on startedAt (millis since epoch).") @QueryParam("since") Long since,
    @Parameter(description = "Inclusive upper bound on startedAt (millis since epoch).") @QueryParam("until") Long until,
    @Parameter(description = "Max rows. Defaults to 100; capped at 1000.") @QueryParam("limit") Integer limit,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean isAdmin = securityContext.isUserInRole("instance-admin");
    // Casual users only see their own rows against this entity; admins see all.
    String agentFilter = isAdmin ? null : caller;

    int eff = limit == null ? 100 : limit;
    OutputProfile prof = outputProfile.getProfile();
    List<ActivityIO> rows = provenance
      .list(agentFilter, null, entityAppId, since, until, eff)
      .stream()
      .map(ActivityIO::from)
      .map(io -> applyProfile(io, prof))
      .toList();
    return Response.ok(rows).build();
  }

  @GET
  @Path("/entity/{appId}")
  @Produces(ProvJsonRenderer.MEDIA_TYPE)
  @Operation(
    summary = "Per-entity provenance trail as W3C PROV-JSON.",
    description = "Same query semantics as the JSON variant of the per-entity endpoint; output shape " +
    "conforms to a small subset of W3C PROV-JSON. Triggered by Accept: application/prov+json."
  )
  @APIResponse(responseCode = "200", description = "Activities serialised as PROV-JSON.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listEntityActivitiesProvJson(
    @PathParam("appId") String entityAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @QueryParam("limit") Integer limit,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean isAdmin = securityContext.isUserInRole("instance-admin");
    String agentFilter = isAdmin ? null : caller;

    int eff = limit == null ? 100 : limit;
    List<Activity> rows = provenance.list(agentFilter, null, entityAppId, since, until, eff);
    return Response.ok(provJsonRenderer.render(rows)).type(ProvJsonRenderer.MEDIA_TYPE).build();
  }

  @GET
  @Path("/entity/{appId}")
  @Produces(ProvJsonLdRenderer.MEDIA_TYPE)
  @Operation(
    summary = "Per-entity provenance trail as JSON-LD (PROV-O / metadata4ing).",
    description = "Same query semantics as the plain-JSON variant. Triggered by " +
    "Accept: application/ld+json; the m4i profile parameter switches to the metadata4ing flavour " +
    "(see /v2/provenance/activities for full content-negotiation rules)."
  )
  @APIResponse(responseCode = "200", description = "Activities serialised as JSON-LD.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "406", description = "Unknown profile= parameter on the Accept header.")
  public Response listEntityActivitiesJsonLd(
    @PathParam("appId") String entityAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @QueryParam("limit") Integer limit,
    @HeaderParam(HttpHeaders.ACCEPT) String acceptHeader,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    boolean isAdmin = securityContext.isUserInRole("instance-admin");
    String agentFilter = isAdmin ? null : caller;

    Response profileError = enforceJsonLdProfile(acceptHeader);
    if (profileError != null) return profileError;
    ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);

    int eff = limit == null ? 100 : limit;
    List<Activity> rows = provenance.list(agentFilter, null, entityAppId, since, until, eff);
    return Response.ok(provJsonLdRenderer.render(rows, profile))
      .type(jsonLdMediaTypeFor(profile))
      .build();
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

  @GET
  @Path("/count")
  @Produces(ProvJsonLdRenderer.MEDIA_TYPE)
  @Operation(
    summary = "Row count as JSON-LD (PROV-O default; metadata4ing profile opt-in).",
    description = "JSON-LD variant of /count — wraps the integer as " +
    "shepard:numberOfActivities under a typed @context. Same query semantics + " +
    "Accept-header profile precedence as the /activities endpoint."
  )
  @APIResponse(responseCode = "200", description = "Row count, JSON-LD wrapped.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller asked for another user's rows without instance-admin role.")
  @APIResponse(responseCode = "406", description = "Unknown profile= parameter on the Accept header.")
  public Response countActivitiesJsonLd(
    @QueryParam("agent") String agent,
    @QueryParam("targetKind") String targetKind,
    @QueryParam("targetAppId") String targetAppId,
    @QueryParam("since") Long since,
    @QueryParam("until") Long until,
    @HeaderParam(HttpHeaders.ACCEPT) String acceptHeader,
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

    Response profileError = enforceJsonLdProfile(acceptHeader);
    if (profileError != null) return profileError;
    ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);

    long c = provenance.count(agent, targetKind, targetAppId, since, until);
    java.util.Map<String, Object> body = provJsonLdRenderer.renderCount(c, profile);
    return Response.ok(body).type(jsonLdMediaTypeFor(profile)).build();
  }

  @GET
  @Path("/stats")
  @Operation(
    summary = "Aggregated provenance stats — totals + sparkline buckets + action-kind histogram.",
    description = "Rolls a single window into one payload for the dashboard. " +
    "scope = instance (admin-only) | collection (any auth user) | user (self or admin). " +
    "Bucket width auto-flips daily → weekly at > 90-day windows. " +
    "Default window = last 90 days when since/until omitted."
  )
  @APIResponse(
    responseCode = "200",
    description = "Stats payload.",
    content = @Content(schema = @Schema(implementation = ProvenanceStatsIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid scope, since > until, or missing id for scope=collection|user.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Non-admin requested scope=instance or another user's stats.")
  public Response stats(
    @Parameter(description = "scope: instance | collection | user.", required = true) @QueryParam("scope") String scope,
    @Parameter(description = "Entity appId for scope=collection, username for scope=user. Ignored for scope=instance.")
    @QueryParam("id") String id,
    @Parameter(description = "Inclusive lower bound on startedAt (millis since epoch). Defaults to 90 days ago.")
    @QueryParam("since") Long since,
    @Parameter(description = "Inclusive upper bound on startedAt (millis since epoch). Defaults to now.")
    @QueryParam("until") Long until,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (scope == null || scope.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("scope is required").build();
    }
    boolean isAdmin = securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE);

    if (ProvenanceStatsService.SCOPE_INSTANCE.equals(scope) && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (ProvenanceStatsService.SCOPE_USER.equals(scope)) {
      if (id == null || id.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("id is required for scope=user").build();
      }
      if (!id.equals(caller) && !isAdmin) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
    }
    if (ProvenanceStatsService.SCOPE_COLLECTION.equals(scope)) {
      if (id == null || id.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("id is required for scope=collection").build();
      }
      // PROV1c-acl: gate on Read permission against the target Collection.
      // Admins bypass the per-Collection check (instance-admin already sees
      // everything elsewhere). Missing Collection → 404; lacking Read → 403.
      if (!isAdmin) {
        var ogmId = collectionPropertiesDAO.findCollectionIdByAppId(id);
        if (ogmId.isEmpty()) {
          return Response.status(Response.Status.NOT_FOUND).entity("No Collection with appId " + id).build();
        }
        if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), de.dlr.shepard.common.util.AccessType.Read, caller, 0L)) {
          return Response.status(Response.Status.FORBIDDEN).build();
        }
      }
    }

    long now = System.currentTimeMillis();
    // Clamp user-supplied bounds to [0, now] before any arithmetic so
    // a malicious or malformed `since`/`until` can't drive the default-
    // window subtraction into underflow (CodeQL/CWE-191).
    Long sinceClamped = since == null ? null : clampToNonNegative(since);
    Long untilClamped = until == null ? null : clampToNonNegative(until);
    long effUntil = untilClamped == null ? now : Math.min(untilClamped, now);
    long defaultWindowMillis = 90L * 86_400_000L;
    long effSince = sinceClamped == null ? Math.max(0L, effUntil - defaultWindowMillis) : Math.min(sinceClamped, effUntil);
    try {
      ProvenanceStatsIO out = statsService.compute(scope, id, effSince, effUntil);
      return Response.ok(out).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }
  }

  /** Clamp a user-supplied millis-since-epoch value to a non-negative long. */
  private static long clampToNonNegative(long v) {
    return Math.max(0L, v);
  }

  /**
   * Validate the {@code profile=} parameter on the {@code Accept} header
   * for the JSON-LD endpoints. Returns a RFC 7807 406 {@link Response}
   * when the parameter is present but unrecognised, otherwise
   * {@code null}.
   *
   * <p>Designed in {@code aidocs/64 §3.2} (PROV1h). The only currently
   * recognised profile is metadata4ing
   * ({@link ProvJsonLdRenderer#M4I_PROFILE_URI} / short
   * {@link ProvJsonLdRenderer#M4I_PROFILE_SHORT}).
   */
  private static Response enforceJsonLdProfile(String acceptHeader) {
    String raw = ProvJsonLdRenderer.extractProfileParam(acceptHeader);
    if (raw == null || raw.isBlank()) return null;
    ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);
    if (profile != null) return null;
    ProblemJson body = new ProblemJson(
      "https://noheton.github.io/shepard/errors/provenance.unsupported-profile",
      "Unsupported JSON-LD profile",
      Response.Status.NOT_ACCEPTABLE.getStatusCode(),
      "The profile= parameter '" + raw + "' on the Accept header is not recognised. " +
      "Supported: '" + ProvJsonLdRenderer.M4I_PROFILE_URI + "' (or short 'metadata4ing'). " +
      "Omit the parameter for plain PROV-O JSON-LD.",
      null
    );
    return Response.status(Response.Status.NOT_ACCEPTABLE)
      .entity(body)
      .type(MediaType.APPLICATION_JSON)
      .build();
  }

  /**
   * Build the JSON-LD content-type string with the {@code profile=}
   * parameter echoed back to the caller when the m4i flavour was
   * selected. The echoed profile lets a caching proxy distinguish
   * the two flavours via {@code Vary: Accept} cleanly.
   */
  private static String jsonLdMediaTypeFor(ProvJsonLdRenderer.ProfileChoice profile) {
    if (profile == ProvJsonLdRenderer.ProfileChoice.M4I) {
      return ProvJsonLdRenderer.MEDIA_TYPE + ";profile=\"" + ProvJsonLdRenderer.M4I_PROFILE_URI + "\"";
    }
    return ProvJsonLdRenderer.MEDIA_TYPE;
  }
}
