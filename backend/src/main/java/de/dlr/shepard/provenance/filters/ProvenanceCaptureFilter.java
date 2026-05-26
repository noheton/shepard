package de.dlr.shepard.provenance.filters;

import de.dlr.shepard.auth.users.daos.MirroredUserDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.MirroredUser;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.MirroredUserEnrichmentCache;
import de.dlr.shepard.provenance.services.ProvenanceService;
import io.quarkus.logging.Log;
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
 *
 * <p><b>PROV-USER-ENRICH</b> — when the request carries
 * {@code X-Source-User-Username} (forwarded by the MFFD v15+ importer),
 * this filter upserts a {@code :MirroredUser} node (idempotent MERGE via
 * {@link MirroredUserDAO}) and stamps the resulting {@code appId} as
 * {@code mirroredUserAppId} on the emitted {@code :Activity} row. The
 * upsert result is cached for 5 minutes per {@code (sourceInstance,
 * sourceUsername)} pair to avoid per-request DB round-trips during
 * importer runs. All prov enrichment steps are best-effort: any failure
 * is logged at WARN and does not block the request.
 */
@Provider
@RequestScoped
public class ProvenanceCaptureFilter implements ContainerRequestFilter, ContainerResponseFilter {

  static final String PROP_STARTED_AT_MILLIS = "shepard.provenance.startedAtMillis";

  /**
   * SEMA-V6-007 — when a handler has already called {@link ProvenanceService#record}
   * directly (e.g. annotation create/update/delete, which need the returned Activity
   * appId to back-stamp {@code :SemanticAnnotation.sourceActivityAppId}), it sets this
   * request property so the response filter skips its own capture and avoids a
   * duplicate {@code :Activity} row.
   *
   * <p>Usage in handlers:
   * <pre>{@code
   *   requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
   * }</pre>
   */
  public static final String PROP_SKIP_CAPTURE = "shepard.provenance.skip-capture";

  /** Header forwarded by the MFFD importer carrying the source-side username. */
  static final String HDR_SOURCE_USERNAME     = "X-Source-User-Username";
  /** Header forwarded by the MFFD importer carrying the source-side display name. */
  static final String HDR_SOURCE_DISPLAY_NAME = "X-Source-User-DisplayName";
  /** Header forwarded by the MFFD importer carrying the source-side email. */
  static final String HDR_SOURCE_EMAIL        = "X-Source-User-Email";
  /** Header forwarded by the MFFD importer carrying the source Shepard instance URL. */
  static final String HDR_SOURCE_INSTANCE     = "X-Source-User-Instance";

  @Inject
  ProvenanceService provenance;

  @Inject
  TargetEntityResolver targetEntityResolver;

  @Inject
  MirroredUserDAO mirroredUserDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  MirroredUserEnrichmentCache enrichmentCache;

  @ConfigProperty(name = "shepard.provenance.capture-reads", defaultValue = "false")
  boolean captureReads;

  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    request.setProperty(PROP_STARTED_AT_MILLIS, System.currentTimeMillis());
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (!provenance.isEnabled()) return;

    // SEMA-V6-007: handler already called ProvenanceService.record() directly
    // and set this property — skip to avoid a duplicate :Activity row.
    if (Boolean.TRUE.equals(request.getProperty(PROP_SKIP_CAPTURE))) return;

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

    // PROV-USER-ENRICH: attempt cross-instance attribution via X-Source-User-* headers.
    // Best-effort — failure here must not prevent the activity row from landing.
    String mirroredUserAppId = resolveMirroredUserAppId(request, principal.getName());

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
      endedAtMillis,
      mirroredUserAppId
    );
  }

  /**
   * PROV-USER-ENRICH — read the four {@code X-Source-User-*} headers and,
   * if the username header is present and non-blank:
   * <ol>
   *   <li>Check the 5-minute {@link MirroredUserEnrichmentCache} for a prior
   *       resolution of this {@code (sourceInstance, sourceUsername)} pair.</li>
   *   <li>On cache miss, call {@link MirroredUserDAO#createOrUpdateBySourceKey}
   *       to upsert the {@code :MirroredUser} node, then cache the result.</li>
   *   <li>Optionally backfill the local {@code :User} node's {@code firstName}/
   *       {@code lastName} from the {@code X-Source-User-DisplayName} header
   *       when those fields are blank (the "kreb_fl → Florian Krebs" fix).</li>
   * </ol>
   *
   * <p>All exceptions are caught and logged at WARN; the method always returns
   * without throwing. Returns {@code null} when no enrichment is applicable.
   *
   * @param request        the inbound request context
   * @param principalName  the JWT principal username (used for the optional
   *                       local-user backfill)
   * @return the resolved {@code :MirroredUser} appId, or {@code null}
   */
  String resolveMirroredUserAppId(ContainerRequestContext request, String principalName) {
    try {
      String sourceUsername = request.getHeaderString(HDR_SOURCE_USERNAME);
      if (sourceUsername == null || sourceUsername.isBlank()) return null;

      String sourceInstance = request.getHeaderString(HDR_SOURCE_INSTANCE);
      String displayName    = request.getHeaderString(HDR_SOURCE_DISPLAY_NAME);
      String email          = request.getHeaderString(HDR_SOURCE_EMAIL);

      // Normalise: blank → null to avoid storing empty strings in the graph.
      if (sourceInstance == null || sourceInstance.isBlank()) sourceInstance = "unknown";

      // Cache-first lookup — avoids a DB round-trip for repeated requests
      // from the same importer session (hundreds per pass in MFFD v16).
      var cached = enrichmentCache.get(sourceInstance, sourceUsername);
      if (cached.isPresent()) {
        return cached.get();
      }

      // Cache miss — upsert the :MirroredUser node.
      MirroredUser incoming = new MirroredUser();
      incoming.setSourceInstance(sourceInstance);
      incoming.setSourceUsername(sourceUsername);
      incoming.setSourceDisplayName(displayName);
      incoming.setSourceEmail(email);

      MirroredUser saved = mirroredUserDAO.createOrUpdateBySourceKey(incoming);
      String appId = saved.getAppId();

      enrichmentCache.put(sourceInstance, sourceUsername, appId);

      // Fallback local-user backfill: if the JWT principal's :User node has
      // blank firstName/lastName and the header carries a display name,
      // attempt to parse and backfill. Strictly best-effort.
      if (displayName != null && !displayName.isBlank() && principalName != null) {
        tryBackfillLocalUser(principalName, displayName, email);
      }

      return appId;
    } catch (RuntimeException e) {
      Log.warnf(e, "PROV-USER-ENRICH: MirroredUser upsert failed — provenance row will land without mirroredUserAppId");
      return null;
    }
  }

  /**
   * Attempt to backfill {@code firstName}/{@code lastName}/{@code email} on the
   * authenticated user's local {@code :User} node when those fields are blank.
   *
   * <p>Parses {@code displayName} by splitting on the first space: everything
   * before the first space becomes {@code firstName}; everything after becomes
   * {@code lastName}. This is a best-effort heuristic — it handles the common
   * "Florian Krebs" case correctly and degrades gracefully for single-token names.
   *
   * <p>Never throws — all failures are swallowed and logged at DEBUG.
   *
   * @param username    the JWT principal username (Neo4j {@code :User} id)
   * @param displayName the value of the {@code X-Source-User-DisplayName} header
   * @param email       the value of the {@code X-Source-User-Email} header (may be null)
   */
  private void tryBackfillLocalUser(String username, String displayName, String email) {
    try {
      User user = userDAO.find(username);
      if (user == null) return;

      boolean firstBlank = user.getFirstName() == null || user.getFirstName().isBlank();
      boolean lastBlank  = user.getLastName()  == null || user.getLastName().isBlank();
      boolean emailBlank = user.getEmail()     == null || user.getEmail().isBlank();

      if (!firstBlank && !lastBlank && !emailBlank) return; // Nothing to backfill.

      if (firstBlank || lastBlank) {
        int sp = displayName.indexOf(' ');
        if (sp > 0) {
          if (firstBlank) user.setFirstName(displayName.substring(0, sp).trim());
          if (lastBlank)  user.setLastName(displayName.substring(sp + 1).trim());
        } else {
          // Single-token display name — put it all in firstName.
          if (firstBlank) user.setFirstName(displayName.trim());
        }
      }
      if (emailBlank && email != null && !email.isBlank()) {
        user.setEmail(email.trim());
      }

      userDAO.createOrUpdate(user);
      Log.debugf("PROV-USER-ENRICH: backfilled :User %s from X-Source-User-* headers", username);
    } catch (RuntimeException e) {
      Log.debugf(e, "PROV-USER-ENRICH: local-user backfill failed for %s — ignored", username);
    }
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
