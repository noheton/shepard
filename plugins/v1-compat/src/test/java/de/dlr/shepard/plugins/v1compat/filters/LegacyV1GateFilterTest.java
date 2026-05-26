package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * V1COMPAT.0 — load-bearing tests for the 410 gate filter. The gate is
 * what makes the operator's runtime flip of
 * {@code :LegacyV1Config.enabled=false} observable to callers; if the
 * wire shape is wrong or the path-detection is wrong, the operator's
 * gesture is silent and the deprecation lever is effectively broken.
 *
 * <p>Uses Vert.x {@link RoutingContext} mocks — the filter is registered
 * via {@code @Observes Filters} (not JAX-RS {@code @Provider}) so the
 * handler receives a Vert.x routing context, not a JAX-RS request context.
 */
class LegacyV1GateFilterTest {

  private LegacyV1ConfigService service;
  private LegacyV1GateFilter filter;

  @BeforeEach
  void setUp() {
    service = mock(LegacyV1ConfigService.class);
    filter = new LegacyV1GateFilter(service);
  }

  @Test
  void v1Path_enabledTrue_callsNext() {
    when(service.isEnabled()).thenReturn(true);
    RoutingContext rc = mockContext("/shepard/api/collections");

    filter.handle(rc);

    verify(rc).next();
    verify(rc.response(), never()).setStatusCode(anyInt());
  }

  @Test
  void v1Path_enabledFalse_emits410WithProblemJsonBody() {
    when(service.isEnabled()).thenReturn(false);
    RoutingContext rc = mockContext("/shepard/api/collections/42");

    filter.handle(rc);

    verify(rc, never()).next();
    HttpServerResponse response = rc.response();
    verify(response).setStatusCode(410);
    verify(response).putHeader("Content-Type", "application/problem+json");
    verify(response).putHeader("Deprecation", "true");
    verify(response).putHeader("Link", "</v2/>; rel=\"successor-version\"");
    verify(response).putHeader("X-Shepard-Legacy", "true");

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).end(bodyCaptor.capture());
    String body = bodyCaptor.getValue();
    assertThat(body).contains("\"type\":\"" + LegacyV1GateFilter.PROBLEM_TYPE_V1_DISABLED + "\"");
    assertThat(body).contains("\"title\":\"" + LegacyV1GateFilter.PROBLEM_TITLE + "\"");
    assertThat(body).contains("\"status\":410");
    assertThat(body).contains("\"detail\":\"" + LegacyV1GateFilter.PROBLEM_DETAIL + "\"");
    assertThat(body).contains("/shepard/api/collections/42");
  }

  @Test
  void v2Path_filterIsNoOp_regardlessOfEnabled() {
    when(service.isEnabled()).thenReturn(false);
    RoutingContext rc = mockContext("/v2/collections/abc");

    filter.handle(rc);

    verify(rc).next();
    verify(rc.response(), never()).setStatusCode(anyInt());
    verify(service, never()).isEnabled();
  }

  @Test
  void healthzPath_filterIsNoOp() {
    when(service.isEnabled()).thenReturn(false);
    RoutingContext rc = mockContext("/healthz");

    filter.handle(rc);

    verify(rc).next();
    verify(service, never()).isEnabled();
  }

  @Test
  void barePrefix_treatedAsV1Path() {
    when(service.isEnabled()).thenReturn(false);
    RoutingContext rc = mockContext("/shepard/api");

    filter.handle(rc);

    verify(rc.response()).setStatusCode(410);
    verify(rc, never()).next();
  }

  @Test
  void unrelatedPrefix_treatedAsNonV1Path() {
    when(service.isEnabled()).thenReturn(false);
    RoutingContext rc = mockContext("/v2/something/shepard/api/thing");

    filter.handle(rc);

    verify(rc).next();
    verify(rc.response(), never()).setStatusCode(anyInt());
  }

  @Test
  void isV1Path_null_returnsFalse() {
    assertThat(LegacyV1GateFilter.isV1Path(null)).isFalse();
  }

  @Test
  void isV1Path_emptyString_returnsFalse() {
    assertThat(LegacyV1GateFilter.isV1Path("")).isFalse();
  }

  @Test
  void isV1Path_v1WithSegment_returnsTrue() {
    assertThat(LegacyV1GateFilter.isV1Path("/shepard/api/collections")).isTrue();
  }

  @Test
  void isV1Path_barePrefix_returnsTrue() {
    assertThat(LegacyV1GateFilter.isV1Path("/shepard/api")).isTrue();
  }

  @Test
  void isV1Path_v2Path_returnsFalse() {
    assertThat(LegacyV1GateFilter.isV1Path("/v2/collections")).isFalse();
  }

  @Test
  void isV1Path_containsV1PrefixButDoesntStartWith_returnsFalse() {
    assertThat(LegacyV1GateFilter.isV1Path("/v2/something/shepard/api/thing")).isFalse();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static RoutingContext mockContext(String path) {
    HttpServerResponse response = mock(HttpServerResponse.class);
    // Builder-pattern stubs so chained calls work
    when(response.setStatusCode(anyInt())).thenReturn(response);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);

    RoutingContext rc = mock(RoutingContext.class);
    when(rc.normalizedPath()).thenReturn(path);
    when(rc.response()).thenReturn(response);
    return rc;
  }
}
