package de.dlr.shepard.plugins.kip.resources;

import de.dlr.shepard.plugins.kip.io.KipRecordIO;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * KIP1a/b — the {@code GET /v2/.well-known/kip/{pid-suffix}} public
 * resolver. Looks up a {@link Publication} by its PID and returns
 * an HMC Kernel Information Profile record per {@code aidocs/66 §3.2}.
 *
 * <p>Unauthenticated by design — mirrors the {@code /versionz} +
 * {@code /v2/aas/.well-known/aas-server} posture (capability metadata,
 * not entity payload). The {@code landingPage} URL the record points
 * at may still require authentication; that's correct per
 * {@code aidocs/66 §4.2}.
 *
 * <p>The PID suffix is matched against the full PID string. PIDs
 * minted by the {@code LocalMinter} (KIP1h) carry colons (e.g.
 * {@code shepard:dlr.de/shepard-prod:data-objects:01HF…:v1}) and the
 * path regex {@code .+} accepts those without URL-encoding. Pre-KIP1h
 * rows minted by the in-core {@code MockMinter} (legacy
 * {@code mock:shepard:<kind>:<appId>:<epoch>} shape) keep resolving
 * cleanly — the resolver does a verbatim {@code findByPid} so the
 * PID format is irrelevant. ePIC handles (KIP1c) and DOIs (KIP1d)
 * typically carry a single slash (e.g. {@code 21.T11148/abc-def}) so
 * the {@code .+} regex absorbs the suffix verbatim too.
 *
 * <p>The {@code .well-known/kip} prefix is registered in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} (core)
 * so {@link de.dlr.shepard.common.filters.JWTFilter} doesn't reject
 * the call before the response is built. The core-side registry
 * tracks plugin-contributed public prefixes by path string; the
 * plugin owning the path doesn't self-register today — see
 * KIP1g's tracker row for the rationale and the PM1d follow-up
 * that would introduce a {@code PluginContext.registerPublicPrefix(...)}
 * API.
 *
 * <p>KIP1g moved this resource from in-tree
 * {@code de.dlr.shepard.v2.publish.resources.KipResolverRest} to
 * the {@code shepard-plugin-kip} module — the resolver implementation
 * + HMC-flavoured record shape together form an "external
 * integration" per CLAUDE.md plugin-first heuristic #2. The wire
 * shape (path, JSON-LD body, RFC 7807 problem responses) is
 * byte-identical to the pre-KIP1g in-tree implementation.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/.well-known/kip")
@RequestScoped
@Tag(name = "KIP resolver (public)")
public class KipResolverRest {

  /**
   * Prefix the {@code PublicEndpointRegistry} bypass uses to admit
   * any {@code /v2/.well-known/kip/...} sub-path through the JWT
   * filter. Kept here as a public constant so future refactors that
   * want to read the canonical prefix string have a single source of
   * truth — the core-side registry hard-codes the same string today.
   */
  public static final String PUBLIC_PATH_PREFIX = "/v2/.well-known/kip";

  @Inject
  PublicationDAO publicationDAO;

  @Inject
  PublishableKindRegistry kindRegistry;

  @GET
  @Path("/{suffix:.+}")
  @Operation(
    summary = "Resolve a PID minted by this shepard to its HMC Kernel Information Profile record.",
    description = "Public endpoint (no auth). Returns a small JSON-LD-flavoured record per " +
    "aidocs/66 §3.2 — `kernelInformationProfile` envelope with `id`, `landingPage`, " +
    "`digitalObjectType`, `dateCreated`, `dateModified`, `rightsHolder`, `license`. " +
    "The landingPage URL is what a casual researcher would visit; that URL may itself " +
    "require authentication, which is correct: KIP records are findability metadata, not " +
    "the underlying entity payload."
  )
  @APIResponse(
    responseCode = "200",
    description = "Found KIP record for the supplied PID suffix.",
    content = @Content(schema = @Schema(implementation = KipRecordIO.class))
  )
  @APIResponse(
    responseCode = "404",
    description = "No Publication matches the supplied PID suffix. RFC 7807 problem+json with type kip.pid.not-found."
  )
  public Response resolve(
    @Parameter(description = "The PID suffix — the verbatim string the active minter returned.", required = true)
    @PathParam("suffix") String suffix,
    @Context UriInfo uriInfo
  ) {
    if (suffix == null || suffix.isBlank()) {
      return problem(
        Response.Status.NOT_FOUND,
        "https://shepard.dlr.de/problems/kip.pid.not-found",
        "No publication with that PID suffix",
        "PID suffix must not be empty."
      );
    }
    Optional<Publication> publication = publicationDAO.findByPid(suffix);
    if (publication.isEmpty()) {
      return problem(
        Response.Status.NOT_FOUND,
        "https://shepard.dlr.de/problems/kip.pid.not-found",
        "No publication with that PID suffix",
        "No :Publication row at this shepard has pid=" + suffix + "."
      );
    }
    Publication p = publication.get();

    String landingPage = landingPage(uriInfo, p);
    String digitalObjectType = digitalObjectType(p);
    String dateCreated = p.getMintedAt() == null ? null : Instant.ofEpochMilli(p.getMintedAt()).toString();
    // KIP1a baseline: dateModified == dateCreated unless future slices
    // surface a separate "Publication updated" timestamp. The KIP spec
    // tolerates this (aidocs/66 §3 marks dateModified "recommended").
    String dateModified = dateCreated;

    // KIP1h — surface the Phase-1 version segment. Pre-KIP1h rows
    // have null versionNumber until V31's backfill runs; default to
    // "v1" so the field is never absent for legacy data.
    Integer version = p.getVersionNumber();
    String digitalObjectVersion = "v" + (version == null || version < 1 ? 1 : version);

    KipRecordIO.KernelInformationProfile body = new KipRecordIO.KernelInformationProfile(
      p.getPid(),
      landingPage,
      digitalObjectType,
      dateCreated,
      dateModified,
      p.getPublishedBy(),
      null,
      digitalObjectVersion
    );
    KipRecordIO record = new KipRecordIO(KipRecordIO.JSONLD_CONTEXT, p.getPid(), body);
    return Response.ok(record).build();
  }

  /**
   * Reconstruct the landing-page URL for the published entity:
   * {@code <shepard.url>/v2/{kind}/{entityAppId}}. The kind is
   * pulled from the Publication's denormalised {@code entityKind}
   * field — that field exists precisely so the resolver doesn't have
   * to walk the inbound {@code HAS_PUBLICATION} edge to figure out
   * the parent's kind.
   */
  String landingPage(UriInfo uriInfo, Publication p) {
    String kindSeg = p.getEntityKind() != null ? p.getEntityKind() : "data-objects";
    return absoluteUrl(uriInfo, "/v2/" + kindSeg + "/" + p.getEntityAppId());
  }

  /**
   * Resolve the KIP {@code digitalObjectType} IRI from the
   * Publication's stamped kind. Falls back to a generic IRI when the
   * stored kind doesn't match a known {@link PublishableKind} — this
   * can only happen if a future slice writes a row then a downgrade
   * runs without that slice's kind registered.
   */
  String digitalObjectType(Publication p) {
    if (p.getEntityKind() == null) {
      return "http://shepard.dlr.de/types/dlr:Unknown";
    }
    return kindRegistry
      .bySegment(p.getEntityKind())
      .map(PublishableKind::digitalObjectType)
      .orElse("http://shepard.dlr.de/types/dlr:Unknown");
  }

  /**
   * Build a fully-qualified URL at the supplied application-relative
   * path (e.g. {@code /v2/data-objects/01HF.../publish}) using the
   * request's own scheme + host + port — i.e. the URL the caller is
   * actually reaching this shepard at. No new config key required.
   *
   * <p>Pre-KIP1g this helper lived as a package-private static method
   * on {@code de.dlr.shepard.v2.publish.resources.PublishRest} and the
   * sibling {@code KipResolverRest} called it directly. After the
   * KIP1g plugin extraction, the resolver lives in a different module
   * + package; the helper is inlined here rather than promoted to a
   * public core API surface (which would constrain future refactors of
   * the in-core publish orchestration).
   */
  static String absoluteUrl(UriInfo uriInfo, String applicationPath) {
    if (uriInfo == null) return applicationPath;
    var base = uriInfo.getBaseUri();
    String scheme = base.getScheme();
    String host = base.getHost();
    int port = base.getPort();
    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);
    if (port > 0 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
      sb.append(":").append(port);
    }
    sb.append(applicationPath.startsWith("/") ? applicationPath : "/" + applicationPath);
    return sb.toString();
  }

  private static Response problem(Response.Status status, String type, String title, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("title", title);
    body.put("status", status.getStatusCode());
    body.put("detail", detail);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
