package de.dlr.shepard.v2.publish.resources;

import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.v2.publish.io.KipRecordIO;
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
 * minted by {@link de.dlr.shepard.publish.minter.MockMinter} carry
 * colons (e.g. {@code mock:shepard:data-objects:01HF…:1747000000000})
 * and the path regex {@code .+} accepts those without URL-encoding.
 * ePIC handles (KIP1c) and DOIs (KIP1d) typically carry a single
 * slash (e.g. {@code 21.T11148/abc-def}) so the {@code .+} regex
 * absorbs the suffix verbatim too.
 *
 * <p>Listed in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} so
 * {@link de.dlr.shepard.common.filters.JWTFilter} doesn't reject the
 * call before the response is built.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/.well-known/kip")
@RequestScoped
@Tag(name = "KIP resolver (public)")
public class KipResolverRest {

  /**
   * Prefix the {@code PublicEndpointRegistry} bypass uses to admit
   * any {@code /v2/.well-known/kip/...} sub-path through the JWT
   * filter. Centralised so the registry can reference it without
   * a fragile string-literal duplication.
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

    KipRecordIO.KernelInformationProfile body = new KipRecordIO.KernelInformationProfile(
      p.getPid(),
      landingPage,
      digitalObjectType,
      dateCreated,
      dateModified,
      p.getPublishedBy(),
      null
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
    return PublishRest.absoluteUrl(uriInfo, "/v2/" + kindSeg + "/" + p.getEntityAppId());
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

  private static Response problem(Response.Status status, String type, String title, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("title", title);
    body.put("status", status.getStatusCode());
    body.put("detail", detail);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
