package de.dlr.shepard.common.exceptions;

import de.dlr.shepard.common.util.Constants;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * Centralised JAX-RS {@link ExceptionMapper} for shepard.
 *
 * <p><b>Two response shapes</b>, picked via the request's {@code Accept}
 * header:
 * <ul>
 *   <li>{@code application/problem+json} (RFC 7807) — the new, default shape
 *       returned for any {@code Accept} that contains
 *       {@code application/problem+json}, an {@code application} wildcard
 *       subtype, or a top-level wildcard (or is empty/missing).</li>
 *   <li>Legacy {@link ApiError} — returned only when the client explicitly
 *       asks for {@code application/json} and does <em>not</em> accept
 *       {@code application/problem+json}. This preserves byte-compatibility
 *       with upstream-gen clients per the API-version policy in
 *       {@code CLAUDE.md}.</li>
 * </ul>
 *
 * <p><b>Information-leak controls.</b> For known shepard exception types we
 * surface the user-facing {@code getMessage()} as the RFC 7807 {@code detail}
 * field; for everything else (the 5xx fall-through) the {@code detail} is a
 * generic message that names a request-scoped trace id, never the raw
 * exception message. This closes the H4 leak vector where Hibernate / Neo4j
 * / Mongo exceptions used to expose query fragments, parameter values, and
 * schema details to clients.
 *
 * <p><b>Logging.</b> Full stack traces are written at {@code debug}; only the
 * trace id, exception class, request method, and request path are written at
 * {@code error}. Request body, headers (auth / api-key / cookie), and
 * exception messages are <em>not</em> logged at {@code error} — those would
 * either leak PII or expand the log-volume DoS surface (subsumes M7).
 */
@Provider
public class ShepardExceptionMapper implements ExceptionMapper<Exception> {

  static final String X_REQUEST_ID_HEADER = "X-Request-Id";
  static final String GENERIC_DETAIL_PREFIX = "An internal server error occurred. Reference: ";

  @Context
  HttpHeaders headers;

  @Context
  UriInfo uriInfo;

  @Context
  Request request;

  @Inject
  TraceIdProvider traceIdProvider;

  @Override
  public Response toResponse(Exception exception) {
    ShepardErrorCode code = mapToCode(exception);
    int status = resolveStatus(exception, code);
    String traceId = resolveTraceId();
    String requestPath = safePath();
    String httpMethod = safeMethod();

    // Stack trace at debug only — preserves diagnosis without flooding
    // ERROR-level log streams or echoing PII into them.
    Log.debugf(exception, "[%s] %s on %s %s", traceId, exception.getClass().getSimpleName(), httpMethod, requestPath);

    // ERROR line carries only the structural fields. No request body, no
    // headers (auth / api-key / cookies), no exception message.
    Log.errorf(
      "[%s] Unhandled %s on %s %s -> HTTP %d",
      traceId,
      exception.getClass().getSimpleName(),
      httpMethod,
      requestPath,
      status
    );

    boolean preferLegacy = wantsLegacyOnly();

    if (preferLegacy) {
      return buildLegacyResponse(exception, code, status);
    }
    return buildProblemResponse(exception, code, status, traceId);
  }

  // Code mapping --------------------------------------------------------

  /**
   * Map an arbitrary exception to its {@link ShepardErrorCode}. Known shepard
   * subclasses get a precise mapping; unknown {@link WebApplicationException}
   * subclasses fall back by HTTP status; everything else is
   * {@link ShepardErrorCode#INTERNAL_UNEXPECTED}.
   */
  static ShepardErrorCode mapToCode(Exception exception) {
    if (exception instanceof InvalidAuthException) {
      // Today's InvalidAuthException is a 403 (forbidden / authorisation
      // failure). 401 cases come in via JAX-RS' NotAuthorizedException.
      return ShepardErrorCode.AUTH_FORBIDDEN;
    }
    if (exception instanceof InvalidBodyException) {
      return ShepardErrorCode.VALIDATION_BODY;
    }
    if (exception instanceof InvalidRequestException || exception instanceof ShepardParserException) {
      return ShepardErrorCode.VALIDATION_FIELD;
    }
    if (exception instanceof InvalidPathException) {
      return ShepardErrorCode.NOT_FOUND_ENTITY;
    }
    if (exception instanceof ShepardProcessingException) {
      return ShepardErrorCode.INTERNAL_UNEXPECTED;
    }
    if (exception instanceof NotAuthorizedException) {
      return ShepardErrorCode.AUTH_UNAUTHENTICATED;
    }
    if (exception instanceof ForbiddenException) {
      return ShepardErrorCode.AUTH_FORBIDDEN;
    }
    if (exception instanceof NotFoundException) {
      return ShepardErrorCode.NOT_FOUND_ENTITY;
    }
    if (exception instanceof WebApplicationException webEx) {
      int status = webEx.getResponse().getStatus();
      if (status == Status.BAD_REQUEST.getStatusCode()) return ShepardErrorCode.VALIDATION_FIELD;
      if (status == Status.UNAUTHORIZED.getStatusCode()) return ShepardErrorCode.AUTH_UNAUTHENTICATED;
      if (status == Status.FORBIDDEN.getStatusCode()) return ShepardErrorCode.AUTH_FORBIDDEN;
      if (status == Status.NOT_FOUND.getStatusCode()) return ShepardErrorCode.NOT_FOUND_ENTITY;
      if (status == Status.CONFLICT.getStatusCode()) return ShepardErrorCode.CONFLICT_ENTITY;
    }
    return ShepardErrorCode.INTERNAL_UNEXPECTED;
  }

  /**
   * The HTTP status to return. For {@link WebApplicationException}s we honour
   * whatever status the throwing site chose so that legacy code paths (which
   * threw with a specific {@link Status}) keep working. Otherwise we use the
   * code's own status — which for the unknown-exception fall-through is 500.
   */
  static int resolveStatus(Exception exception, ShepardErrorCode code) {
    if (exception instanceof WebApplicationException webEx) {
      return webEx.getResponse().getStatus();
    }
    return code.httpStatus();
  }

  /**
   * Whether the exception is one of shepard's known controlled types whose
   * {@code getMessage()} is safe to surface to the client. For everything
   * else the message can carry Hibernate / Neo4j / Mongo internals and must
   * be replaced with a generic detail.
   */
  static boolean isKnownShepardException(Exception exception) {
    return exception instanceof ShepardException || exception instanceof WebApplicationException;
  }

  // Content negotiation -------------------------------------------------

  /**
   * The legacy {@link ApiError} shape is returned only if the client
   * explicitly accepts {@code application/json} and does <em>not</em>
   * accept {@code application/problem+json} or any wildcard. This preserves
   * upstream-client wire compatibility while making problem+json the
   * default for everything else (including a wildcard {@code Accept}).
   */
  boolean wantsLegacyOnly() {
    if (headers == null) return false;
    List<MediaType> accepted;
    try {
      accepted = headers.getAcceptableMediaTypes();
    } catch (Exception e) {
      return false;
    }
    if (accepted == null || accepted.isEmpty()) return false;

    boolean acceptsLegacyJson = false;
    boolean acceptsProblemJsonOrWildcard = false;

    for (MediaType mt : accepted) {
      if (mt.isWildcardType()) {
        acceptsProblemJsonOrWildcard = true;
        continue;
      }
      String type = mt.getType();
      String subtype = mt.getSubtype();
      if ("application".equalsIgnoreCase(type)) {
        if (mt.isWildcardSubtype() || "*".equals(subtype)) {
          acceptsProblemJsonOrWildcard = true;
        } else if ("problem+json".equalsIgnoreCase(subtype)) {
          acceptsProblemJsonOrWildcard = true;
        } else if ("json".equalsIgnoreCase(subtype)) {
          acceptsLegacyJson = true;
        }
      }
    }

    return acceptsLegacyJson && !acceptsProblemJsonOrWildcard;
  }

  // Response shapes -----------------------------------------------------

  Response buildProblemResponse(Exception exception, ShepardErrorCode code, int status, String traceId) {
    ProblemJson body = new ProblemJson(code.typeUrl(), code.defaultTitle(), status, sanitisedDetail(exception, traceId), traceId);
    return Response.status(status).entity(body).type(Constants.APPLICATION_PROBLEM_JSON).build();
  }

  Response buildLegacyResponse(Exception exception, ShepardErrorCode code, int status) {
    // Legacy clients that haven't upgraded to RFC 7807 still get the old
    // shape; but the message is sanitised the same way as problem+json so
    // the H4 leak doesn't reopen via the legacy path.
    String message = sanitisedDetail(exception, resolveTraceId());
    String exceptionName = exception.getClass().getSimpleName();
    return Response.status(status)
      .entity(new ApiError(status, exceptionName, message))
      .type(MediaType.APPLICATION_JSON)
      .build();
  }

  /**
   * The detail string surfaced to the client.
   *
   * <ul>
   *   <li>Known shepard exceptions: their {@code getMessage()} (which may be
   *       null — a null detail simply gets omitted from the serialised
   *       response per {@link com.fasterxml.jackson.annotation.JsonInclude
   *       NON_NULL}).</li>
   *   <li>Anything else: the generic-5xx fallback referencing the trace id.
   *       Never the raw exception message.</li>
   * </ul>
   */
  static String sanitisedDetail(Exception exception, String traceId) {
    if (isKnownShepardException(exception)) {
      return exception.getMessage();
    }
    return GENERIC_DETAIL_PREFIX + traceId;
  }

  // Request-context helpers ---------------------------------------------

  String resolveTraceId() {
    if (headers != null) {
      try {
        String headerValue = headers.getHeaderString(X_REQUEST_ID_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
          return headerValue.trim();
        }
      } catch (Exception ignored) {
        // Fall through to the request-scoped UUID.
      }
    }
    return traceIdProvider != null ? traceIdProvider.getTraceId() : "unknown";
  }

  String safePath() {
    if (uriInfo == null) return "<unknown>";
    try {
      String path = uriInfo.getPath();
      return path != null ? path : "<unknown>";
    } catch (Exception e) {
      return "<unknown>";
    }
  }

  String safeMethod() {
    if (request == null) return "<unknown>";
    try {
      String m = request.getMethod();
      return m != null ? m : "<unknown>";
    } catch (Exception e) {
      return "<unknown>";
    }
  }
}
