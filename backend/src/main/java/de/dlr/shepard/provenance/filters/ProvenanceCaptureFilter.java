package de.dlr.shepard.provenance.filters;

import de.dlr.shepard.provenance.services.ProvenanceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS filter that lands one provenance {@link
 * de.dlr.shepard.provenance.entities.Activity} row per mutating
 * request that returns a 2xx response. Designed in {@code aidocs/55 §4}.
 *
 * <p>Mutating methods are POST / PUT / PATCH / DELETE.
 * Reads (GET / HEAD / OPTIONS) are captured only when
 * {@code shepard.provenance.capture-reads=true}; default off so
 * activity-log volume stays bounded.
 *
 * <p>The filter implements both
 * {@link ContainerRequestFilter} (to stamp the start-time millis
 * into a request property at request-start) and
 * {@link ContainerResponseFilter} (to write the row at request-end
 * once the response status is known).
 */
@Provider
@RequestScoped
public class ProvenanceCaptureFilter implements ContainerRequestFilter, ContainerResponseFilter {

  static final String PROP_STARTED_AT_MILLIS = "shepard.provenance.startedAtMillis";

  @Inject
  ProvenanceService provenance;

  @Inject
  TargetEntityResolver targetEntityResolver;

  @ConfigProperty(name = "shepard.provenance.capture-reads", defaultValue = "false")
  boolean captureReads;

  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    request.setProperty(PROP_STARTED_AT_MILLIS, System.currentTimeMillis());
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (!provenance.isEnabled()) return;

    String method = request.getMethod();
    boolean isMutation = isMutation(method);
    if (!isMutation && !captureReads) return;

    int status = response.getStatus();
    // Only capture successful writes; failures aren't activities in
    // the PROV-O sense (an Activity that failed isn't observed
    // state-change).
    if (status < 200 || status >= 300) return;

    var principal = request.getSecurityContext() != null ? request.getSecurityContext().getUserPrincipal() : null;
    if (principal == null) return; // No agent → no activity row.

    long endedAtMillis = System.currentTimeMillis();
    Object startedObj = request.getProperty(PROP_STARTED_AT_MILLIS);
    long startedAtMillis = startedObj instanceof Long s ? s : endedAtMillis;

    String path = request.getUriInfo().getPath();
    String summary = method + " /" + (path == null ? "" : path);
    String actionKind = actionKindFor(method);

    // Right-to-left path walk + numeric-id resolution (PROV-RESOLVER-PATHWALK
    // + PROV-V1-NUMERIC-LOOKUP, closes RDM-2026-05-24-004 buckets B + C).
    var target = targetEntityResolver.resolve(path);
    String targetKind = target.map(TargetEntityResolver.TargetRef::kind).orElse(null);
    String targetAppId = target.map(TargetEntityResolver.TargetRef::appId).orElse(null);

    provenance.record(
      actionKind,
      targetKind,
      targetAppId,
      principal.getName(),
      summary,
      method,
      path,
      status,
      startedAtMillis,
      endedAtMillis
    );
  }

  private static boolean isMutation(String method) {
    return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
  }

  /** Maps an HTTP method onto a PROV-O-friendly {@code actionKind} string. */
  static String actionKindFor(String method) {
    return switch (method) {
      case "POST" -> "CREATE";
      case "PUT", "PATCH" -> "UPDATE";
      case "DELETE" -> "DELETE";
      case "GET", "HEAD" -> "READ";
      default -> "EXECUTE";
    };
  }
}
