package de.dlr.shepard.common.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShepardExceptionMapperTest {

  private ShepardExceptionMapper mapper;
  private HttpHeaders headers;
  private UriInfo uriInfo;
  private Request request;
  private TraceIdProvider traceIdProvider;

  @BeforeAll
  static void attachLogCapture() {
    ExceptionMapperLogCapture.attach();
  }

  @BeforeEach
  void setUp() {
    mapper = new ShepardExceptionMapper();
    headers = mock(HttpHeaders.class);
    uriInfo = mock(UriInfo.class);
    request = mock(Request.class);
    traceIdProvider = mock(TraceIdProvider.class);

    lenient().when(uriInfo.getPath()).thenReturn("/shepard/api/test");
    lenient().when(request.getMethod()).thenReturn("GET");
    lenient().when(traceIdProvider.getTraceId()).thenReturn("trace-fixed-uuid");
    lenient().when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));
    lenient().when(headers.getHeaderString(ShepardExceptionMapper.X_REQUEST_ID_HEADER)).thenReturn(null);

    mapper.headers = headers;
    mapper.uriInfo = uriInfo;
    mapper.request = request;
    mapper.traceIdProvider = traceIdProvider;

    ExceptionMapperLogCapture.reset();
  }

  // ─── 5xx leak suppression ────────────────────────────────────────────

  @Test
  public void leakyRuntimeExceptionDoesNotEscapeIntoResponseBody() {
    var leaky = new RuntimeException("schema validation failed near column users.password_hash");

    Response response = mapper.toResponse(leaky);

    assertEquals(500, response.getStatus());
    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());

    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertEquals(ShepardErrorCode.INTERNAL_UNEXPECTED.typeUrl(), body.type());
    assertEquals(500, body.status());
    assertNotNull(body.detail());
    assertFalse(body.detail().contains("password_hash"), "leaked column name in detail: " + body.detail());
    assertFalse(body.detail().contains("schema validation"), "leaked schema fragment in detail: " + body.detail());
    assertTrue(body.detail().startsWith("An internal server error occurred. Reference: "), body.detail());
    assertEquals("trace-fixed-uuid", body.instance());
  }

  @Test
  public void leakyMessageIsLoggedOnlyAtDebug_notError() {
    var leaky = new RuntimeException("schema validation failed near column users.password_hash");

    mapper.toResponse(leaky);

    assertTrue(
      ExceptionMapperLogCapture.errorMessages().stream().noneMatch(m -> m.contains("password_hash")),
      "exception message leaked into ERROR-level logs: " + ExceptionMapperLogCapture.errorMessages()
    );
    // The DEBUG line carries the exception (full stack trace) — Java
    // Logging records that as the LogRecord.thrown attached to the message.
    assertTrue(
      ExceptionMapperLogCapture.debugThrownMessages().stream().anyMatch(m -> m.contains("password_hash")),
      "expected leaky message captured at DEBUG via record.getThrown(); saw: " +
      ExceptionMapperLogCapture.debugThrownMessages()
    );
  }

  @Test
  public void errorLineCarriesOnlyTraceIdClassMethodPath() {
    var leaky = new RuntimeException("schema validation failed near column users.password_hash");

    mapper.toResponse(leaky);

    var errorMessages = ExceptionMapperLogCapture.errorMessages();
    assertTrue(errorMessages.stream().anyMatch(m -> m.contains("trace-fixed-uuid")), errorMessages.toString());
    assertTrue(errorMessages.stream().anyMatch(m -> m.contains("RuntimeException")), errorMessages.toString());
    assertTrue(errorMessages.stream().anyMatch(m -> m.contains("/shepard/api/test")), errorMessages.toString());
    assertTrue(errorMessages.stream().anyMatch(m -> m.contains("GET")), errorMessages.toString());
  }

  // ─── Known-exception mapping ─────────────────────────────────────────

  @Test
  public void notAuthorizedExceptionMapsTo401Unauthenticated() {
    Response response = mapper.toResponse(new NotAuthorizedException("Bearer"));

    assertEquals(401, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("auth.unauthenticated"), body.type());
    assertEquals("Authentication required", body.title());
    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
  }

  @Test
  public void forbiddenExceptionMapsTo403() {
    Response response = mapper.toResponse(new ForbiddenException("nope"));

    assertEquals(403, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("auth.forbidden"), body.type());
    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
  }

  @Test
  public void invalidAuthExceptionMapsTo403() {
    Response response = mapper.toResponse(new InvalidAuthException("permission policy says no"));

    assertEquals(403, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("auth.forbidden"), body.type());
    // Known shepard exception — message surfaced as detail.
    assertEquals("permission policy says no", body.detail());
  }

  @Test
  public void notFoundExceptionMapsTo404() {
    Response response = mapper.toResponse(new NotFoundException("missing thing"));

    assertEquals(404, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("not_found.entity"), body.type());
    assertEquals("missing thing", body.detail());
  }

  @Test
  public void invalidPathExceptionMapsTo404() {
    Response response = mapper.toResponse(new InvalidPathException("does not exist"));

    assertEquals(404, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("not_found.entity"), body.type());
    assertEquals("does not exist", body.detail());
  }

  @Test
  public void invalidBodyExceptionMapsTo400ValidationBody() {
    Response response = mapper.toResponse(new InvalidBodyException("required field missing"));

    assertEquals(400, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("validation.body"), body.type());
    assertEquals("required field missing", body.detail());
  }

  @Test
  public void webApplicationExceptionConflictMapsTo409() {
    Response response = mapper.toResponse(new WebApplicationException("conflict", Status.CONFLICT));

    assertEquals(409, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertTrue(body.type().endsWith("conflict.entity"), body.type());
  }

  // ─── Content negotiation ─────────────────────────────────────────────

  @Test
  public void acceptApplicationJsonExplicitGetsLegacyApiError() {
    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

    Response response = mapper.toResponse(new InvalidAuthException("forbidden"));

    assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
    var legacy = assertInstanceOf(ApiError.class, response.getEntity());
    assertEquals(403, legacy.getStatus());
    assertEquals("InvalidAuthException", legacy.getException());
    assertEquals("forbidden", legacy.getMessage());
  }

  @Test
  public void acceptProblemJsonGetsRfc7807Shape() {
    when(headers.getAcceptableMediaTypes())
      .thenReturn(List.of(MediaType.valueOf(Constants.APPLICATION_PROBLEM_JSON)));

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
    assertInstanceOf(ProblemJson.class, response.getEntity());
  }

  @Test
  public void acceptWildcardDefaultsToProblemJson() {
    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
    assertInstanceOf(ProblemJson.class, response.getEntity());
  }

  @Test
  public void acceptApplicationStarSubtypeDefaultsToProblemJson() {
    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.valueOf("application/*")));

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
  }

  @Test
  public void acceptBothJsonAndProblemJsonPrefersProblemJson() {
    when(headers.getAcceptableMediaTypes())
      .thenReturn(
        List.of(MediaType.APPLICATION_JSON_TYPE, MediaType.valueOf(Constants.APPLICATION_PROBLEM_JSON))
      );

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
  }

  @Test
  public void emptyAcceptDefaultsToProblemJson() {
    when(headers.getAcceptableMediaTypes()).thenReturn(List.of());

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    assertEquals(Constants.APPLICATION_PROBLEM_JSON, response.getMediaType().toString());
  }

  // ─── Trace id sourcing ───────────────────────────────────────────────

  @Test
  public void xRequestIdHeaderHonouredAsInstance() {
    when(headers.getHeaderString(ShepardExceptionMapper.X_REQUEST_ID_HEADER)).thenReturn("client-supplied-id");

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertEquals("client-supplied-id", body.instance());
  }

  @Test
  public void missingXRequestIdFallsBackToTraceIdProvider() {
    when(headers.getHeaderString(ShepardExceptionMapper.X_REQUEST_ID_HEADER)).thenReturn(null);

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertEquals("trace-fixed-uuid", body.instance());
  }

  @Test
  public void blankXRequestIdFallsBackToTraceIdProvider() {
    when(headers.getHeaderString(ShepardExceptionMapper.X_REQUEST_ID_HEADER)).thenReturn("   ");

    Response response = mapper.toResponse(new InvalidAuthException("nope"));

    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertEquals("trace-fixed-uuid", body.instance());
  }

  // ─── Nullable detail ─────────────────────────────────────────────────

  @Test
  public void nullExceptionMessageOnKnownTypeOmitsDetail() {
    // Known shepard exception with no message: detail field is null and
    // omitted from serialised body (NON_NULL include strategy).
    var ex = new ShepardException(null, Status.NOT_FOUND) {
      private static final long serialVersionUID = 1L;
    };
    Response response = mapper.toResponse(ex);

    assertEquals(404, response.getStatus());
    var body = assertInstanceOf(ProblemJson.class, response.getEntity());
    assertNull(body.detail());
  }

  // ─── Reverse-proxy root-probe suppression ────────────────────────────

  @Test
  public void reverseProxyRootProbeNotFoundOnSlashSuppressesErrorLog() {
    // Zoraxy probes GET / on the backend port every few seconds.  The backend
    // has no root handler so JAX-RS throws NotFoundException.  This must NOT
    // produce an ERROR log line — only the debug stack-trace is acceptable.
    when(uriInfo.getPath()).thenReturn("/");
    when(request.getMethod()).thenReturn("GET");

    mapper.toResponse(new NotFoundException());

    assertTrue(
      ExceptionMapperLogCapture.errorMessages().isEmpty(),
      "Expected no ERROR log for GET / probe, but got: " + ExceptionMapperLogCapture.errorMessages()
    );
  }

  @Test
  public void reverseProxyRootProbeNotFoundOnEmptyPathSuppressesErrorLog() {
    // JAX-RS / Quarkus may return "" rather than "/" for the root path.
    when(uriInfo.getPath()).thenReturn("");
    when(request.getMethod()).thenReturn("GET");

    mapper.toResponse(new NotFoundException());

    assertTrue(
      ExceptionMapperLogCapture.errorMessages().isEmpty(),
      "Expected no ERROR log for GET (empty-path) probe, but got: " + ExceptionMapperLogCapture.errorMessages()
    );
  }

  @Test
  public void notFoundOnRealApiPathStillLogsError() {
    // A genuine 404 on a real endpoint path must still reach ERROR level.
    when(uriInfo.getPath()).thenReturn("/shepard/api/collections/999");
    when(request.getMethod()).thenReturn("GET");

    mapper.toResponse(new NotFoundException("not found"));

    assertFalse(
      ExceptionMapperLogCapture.errorMessages().isEmpty(),
      "Expected ERROR log for GET /shepard/api/collections/999 but got none"
    );
    assertTrue(
      ExceptionMapperLogCapture.errorMessages().stream().anyMatch(m -> m.contains("NotFoundException")),
      "ERROR log should name the exception class: " + ExceptionMapperLogCapture.errorMessages()
    );
  }

  @Test
  public void postOnRootPathStillLogsError() {
    // Only GET / is a known probe signature.  POST / is anomalous and must
    // still reach ERROR level.
    when(uriInfo.getPath()).thenReturn("/");
    when(request.getMethod()).thenReturn("POST");

    mapper.toResponse(new NotFoundException());

    assertFalse(
      ExceptionMapperLogCapture.errorMessages().isEmpty(),
      "Expected ERROR log for POST / but got none"
    );
  }

  @Test
  public void isReverseProxyRootProbeReturnsTrueForGetSlash() {
    assertTrue(ShepardExceptionMapper.isReverseProxyRootProbe(new NotFoundException(), "GET", "/"));
  }

  @Test
  public void isReverseProxyRootProbeReturnsTrueForGetEmptyPath() {
    assertTrue(ShepardExceptionMapper.isReverseProxyRootProbe(new NotFoundException(), "GET", ""));
  }

  @Test
  public void isReverseProxyRootProbeReturnsTrueForGetNullPath() {
    assertTrue(ShepardExceptionMapper.isReverseProxyRootProbe(new NotFoundException(), "GET", null));
  }

  @Test
  public void isReverseProxyRootProbeReturnsFalseForNonGetMethod() {
    assertFalse(ShepardExceptionMapper.isReverseProxyRootProbe(new NotFoundException(), "POST", "/"));
  }

  @Test
  public void isReverseProxyRootProbeReturnsFalseForNonRootPath() {
    assertFalse(ShepardExceptionMapper.isReverseProxyRootProbe(new NotFoundException(), "GET", "/shepard/api"));
  }

  @Test
  public void isReverseProxyRootProbeReturnsFalseForNonNotFoundException() {
    assertFalse(ShepardExceptionMapper.isReverseProxyRootProbe(new ForbiddenException(), "GET", "/"));
  }
}
