package de.dlr.shepard.data.timeseries.sql;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.configuration.feature.toggles.SqlTimeseriesFeatureToggle;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * P10a/P10b — {@code POST /v2/sql/timeseries}: JSON DSL bulk read endpoint for timeseries data.
 *
 * <p>Accepts a {@link SqlQuerySpec} body, resolves the caller's permission-gated container IDs
 * via {@link PermissionsService#filterAllowedForUser}, compiles the spec into a
 * {@link PreparedStatementSpec}, and streams results in the negotiated format.
 *
 * <p><b>P10b content negotiation</b> (preference order):
 * <ol>
 *   <li>{@code text/csv} — RFC 4180 CSV with header row; default for {@code Accept: *&#47;*}
 *       (per {@code aidocs/29 §5}: "timeseries to Excel" is the primary use case).</li>
 *   <li>{@code application/json} — {@code {"rows":[…],"truncated":bool}} envelope.</li>
 *   <li>{@code application/x-ndjson} — one JSON object per line, {@code \n} delimited.</li>
 * </ol>
 *
 * <p><b>P10b streaming caps</b>:
 * <ul>
 *   <li>Row cap: {@code shepard.timeseries.sql.max-rows} (default 1,000,000). When reached,
 *       the HTTP trailer {@code x-shepard-truncated: true} is emitted and the stream closes
 *       cleanly.</li>
 *   <li>Duration cap: {@code shepard.timeseries.sql.max-duration} (default {@code PT60S}).
 *       Implemented via PostgreSQL {@code statement_timeout}. On timeout, HTTP 504 is returned
 *       with a structured JSON error body.</li>
 * </ul>
 *
 * <p><b>Trailer</b>: {@code x-shepard-truncated: true} is emitted via VertX
 * {@link HttpServerResponse#putTrailer} when the row cap fires. The {@code Trailer} announcement
 * header is included in the initial response headers so HTTP/1.1 proxies forward it correctly.
 *
 * <p>Gated on {@code shepard.timeseries.sql.enabled} (default {@code true} since P10c);
 * returns 404 when disabled.
 *
 * <p>See {@code aidocs/platform/29-p10-implementation-design.md §5–§6} for format and streaming
 * specs, and {@code §11} for the P10a/P10b/P10c rollout plan.
 */
@Path("/v2/sql/timeseries")
@RequestScoped
public class SqlTimeseriesRest {

  /** Hard cap on the number of container IDs per request. */
  private static final int MAX_CONTAINERS = 1000;

  /** Trailer header name announced in the response and set after stream end. */
  static final String TRAILER_TRUNCATED = "x-shepard-truncated";

  /** MIME type constant for NDJSON (not in {@link MediaType}). */
  static final String NDJSON_TYPE = "application/x-ndjson";

  @Inject
  SqlQueryCompiler compiler;

  @Inject
  SqlQueryExecutor executor;

  @Inject
  PermissionsService permissionsService;

  /**
   * P10c: runtime-mutable config singleton. Effective max-rows and max-duration
   * are read from this service (which falls back to deploy-time defaults when the
   * singleton fields are null). The deploy-time {@code @ConfigProperty} keys are
   * still honoured — they seed the singleton on first start.
   */
  @Inject
  SqlTimeseriesConfigService configService;

  /**
   * Execute a timeseries SQL DSL query and return matching rows in the negotiated format.
   *
   * <p>Format selection (first match wins):
   * <ol>
   *   <li>null / blank / {@code *&#47;*} → CSV</li>
   *   <li>contains {@code text/csv} → CSV</li>
   *   <li>contains {@code application/json} → JSON</li>
   *   <li>contains {@code application/x-ndjson} → NDJSON</li>
   *   <li>otherwise → CSV</li>
   * </ol>
   *
   * @param spec            the JSON DSL request body
   * @param acceptHeader    the raw {@code Accept} header value (may be null)
   * @param httpResponse    the VertX HTTP server response; used to emit the truncation trailer
   * @param securityContext the JAX-RS security context
   * @return 200 on success; 400 on bad DSL; 404 if feature disabled; 504 on query timeout
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces({"text/csv", MediaType.APPLICATION_JSON, NDJSON_TYPE})
  public Response query(
      @Valid @NotNull SqlQuerySpec spec,
      @HeaderParam("Accept") String acceptHeader,
      @Context HttpServerResponse httpResponse,
      @Context SecurityContext securityContext) {
    if (!SqlTimeseriesFeatureToggle.isActive()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return executeQuery(spec, acceptHeader, httpResponse, securityContext);
  }

  /**
   * Core query logic — package-private so unit tests can call it directly without
   * bootstrapping MicroProfile Config (which {@link SqlTimeseriesFeatureToggle#isActive()}
   * requires). The toggle gate lives only in {@link #query}.
   *
   * <p>The {@code httpResponse} parameter may be {@code null} in unit tests that do not set up
   * a full VertX context; trailer emission is skipped gracefully when it is null.
   */
  Response executeQuery(SqlQuerySpec spec, String acceptHeader,
      HttpServerResponse httpResponse, SecurityContext securityContext) {

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

    // P10c: read effective caps from the runtime-mutable config singleton.
    // Falls back to deploy-time defaults (application.properties) when the
    // singleton fields are null or not yet seeded.
    long maxDurationMs = parseDurationMs(configService.effectiveMaxDurationIso());
    int maxRows = spec.limit() != null
        ? (int) Math.min(spec.limit(), configService.effectiveMaxRows())
        : (int) configService.effectiveMaxRows();

    // Empty allowed set — fast path: no DB round-trip needed
    if (allowed.isEmpty()) {
      return emptyResponse(acceptHeader);
    }

    PreparedStatementSpec compiled;
    try {
      compiled = compiler.compile(spec, allowed);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }

    // Announce the trailer so HTTP/1.1 proxies forward it
    if (httpResponse != null) {
      httpResponse.putHeader("Trailer", TRAILER_TRUNCATED);
    }

    Format format = negotiateFormat(acceptHeader);
    return buildStreamingResponse(format, compiled, maxRows, maxDurationMs, httpResponse);
  }

  /**
   * Builds the streaming {@link Response} for the chosen format.
   *
   * <p>The {@link StreamingOutput} lambda executes on the response thread when the container
   * serialises the response body. If {@link SqlQueryExecutor.QueryTimeoutException} fires
   * inside the lambda (i.e., the PostgreSQL {@code statement_timeout} fired before or during
   * {@code stmt.executeQuery()} — before any response byte is written), the exception is
   * propagated directly as a {@link RuntimeException} so that {@link QueryTimeoutExceptionMapper}
   * can intercept it and return HTTP 504 with a structured JSON body.
   *
   * <p>If the timeout fires mid-stream (after the first row is fetched and headers are committed),
   * JAX-RS has already written the 200 status; in that case the container closes the connection
   * and the 504 body cannot be set. This is documented in {@link QueryTimeoutExceptionMapper}.
   */
  private Response buildStreamingResponse(Format format, PreparedStatementSpec compiled,
      int maxRows, long maxDurationMs, HttpServerResponse httpResponse) {

    StreamingOutput stream = out -> {
      WriteResult result = switch (format) {
        case CSV -> executor.executeCsv(compiled, maxRows, maxDurationMs, out);
        case JSON -> executor.executeJson(compiled, maxRows, maxDurationMs, out);
        case NDJSON -> executor.executeNdjson(compiled, maxRows, maxDurationMs, out);
      };
      emitTrailer(httpResponse, result.truncated());
    };

    return switch (format) {
      case CSV -> Response.ok(stream)
          .type("text/csv; charset=UTF-8")
          .header("Content-Disposition", "attachment; filename=\"timeseries.csv\"")
          .build();
      case JSON -> Response.ok(stream).type(MediaType.APPLICATION_JSON).build();
      case NDJSON -> Response.ok(stream).type(NDJSON_TYPE).build();
    };
  }

  private void emitTrailer(HttpServerResponse httpResponse, boolean truncated) {
    if (httpResponse != null && truncated) {
      httpResponse.putTrailer(TRAILER_TRUNCATED, "true");
    }
  }

  /** Empty-result response for the empty-allowed-set fast path. */
  private Response emptyResponse(String acceptHeader) {
    Format format = negotiateFormat(acceptHeader);
    return switch (format) {
      case CSV -> Response.ok("").type("text/csv; charset=UTF-8")
          .header("Content-Disposition", "attachment; filename=\"timeseries.csv\"")
          .build();
      case NDJSON -> Response.ok("").type(NDJSON_TYPE).build();
      default -> Response.ok("{\"rows\":[],\"truncated\":false}").type(MediaType.APPLICATION_JSON).build();
    };
  }

  /**
   * Selects the output format from the raw {@code Accept} header string.
   *
   * <p>Preference order per {@code aidocs/29 §5}: CSV {@literal >} JSON {@literal >} NDJSON.
   * {@code null}, blank, or {@code *&#47;*} → CSV (primary use case is Excel).
   */
  static Format negotiateFormat(String accept) {
    if (accept == null || accept.isBlank() || accept.contains("*/*")) {
      return Format.CSV;
    }
    if (accept.contains("text/csv")) {
      return Format.CSV;
    }
    if (accept.contains("application/json")) {
      return Format.JSON;
    }
    if (accept.contains("application/x-ndjson")) {
      return Format.NDJSON;
    }
    return Format.CSV; // default
  }

  /** Parses an ISO-8601 duration string to milliseconds. Returns 0 on parse failure (no limit). */
  static long parseDurationMs(String iso8601) {
    try {
      return Duration.parse(iso8601).toMillis();
    } catch (Exception e) {
      return 0L;
    }
  }

  enum Format {
    CSV, JSON, NDJSON
  }
}
