package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — covers the instrumentation (counter increment + first-hit
 * dedup + write-method audit emission) and the deprecation header emission
 * for {@link LegacyV1DeprecationFilter}.
 *
 * <p>Uses Vert.x {@link RoutingContext} mocks — the filter is registered
 * via {@code @Observes Filters}, not JAX-RS {@code @Provider}.
 *
 * <p><b>Principal note.</b> Because this filter runs before the JAX-RS
 * JWTFilter, no {@code SecurityContext} is available. All v1 hits are
 * recorded under the principal {@code "anonymous"} (Phase 1 behaviour).
 */
class LegacyV1DeprecationFilterTest {

  private LegacyV1StatsService stats;
  private LegacyV1DeprecationFilter filter;

  @BeforeEach
  void setUp() {
    stats = new LegacyV1StatsService();
    filter = new LegacyV1DeprecationFilter(stats);
  }

  // ─── stats recording ─────────────────────────────────────────────────

  @Test
  void v1Path_recordsHit() {
    filter.handle(mockContext("/shepard/api/collections/42", "GET"));

    assertThat(stats.getTotalHits()).isEqualTo(1);
    assertThat(stats.snapshot().byEndpoint())
      .extracting(c -> c.pathPattern())
      .containsExactly("/shepard/api/collections");
  }

  @Test
  void v1Path_principalAlwaysAnonymous() {
    filter.handle(mockContext("/shepard/api/collections/42", "GET"));

    assertThat(stats.snapshot().byPrincipal())
      .extracting(p -> p.principalSub())
      .containsExactly("anonymous");
  }

  @Test
  void v1Path_aggregatesByEndpointFamily() {
    filter.handle(mockContext("/shepard/api/collections/1", "GET"));
    filter.handle(mockContext("/shepard/api/collections/2", "GET"));
    filter.handle(mockContext("/shepard/api/collections/42/dataObjects/7", "GET"));

    assertThat(stats.snapshot().byEndpoint())
      .extracting(c -> c.pathPattern(), c -> c.hits())
      .containsExactly(org.assertj.core.groups.Tuple.tuple("/shepard/api/collections", 3L));
  }

  @Test
  void v2Path_isNoOp() {
    filter.handle(mockContext("/v2/collections/abc", "GET"));
    assertThat(stats.getTotalHits()).isZero();
  }

  // ─── deprecation headers ─────────────────────────────────────────────

  @Test
  void v1Path_emitsThreeHeaders() {
    RoutingContext rc = mockContext("/shepard/api/collections", "GET");

    filter.handle(rc);

    HttpServerResponse response = rc.response();
    verify(response).putHeader("Deprecation", "true");
    verify(response).putHeader("Link", "</v2/>; rel=\"successor-version\"");
    verify(response).putHeader("X-Shepard-Legacy", "true");
  }

  @Test
  void v2Path_emitsNoHeaders() {
    RoutingContext rc = mockContext("/v2/collections", "GET");

    filter.handle(rc);

    verify(rc.response(), never()).putHeader(anyString(), anyString());
    verify(rc).next(); // still continues the chain for v2
  }

  @Test
  void v1Path_callsNextAfterSettingHeaders() {
    RoutingContext rc = mockContext("/shepard/api/users/me", "GET");

    filter.handle(rc);

    verify(rc).next();
  }

  // ─── path-pattern helper ─────────────────────────────────────────────

  @Test
  void pathPattern_extractsResourceFamily() {
    assertThat(LegacyV1DeprecationFilter.pathPattern("/shepard/api/collections"))
      .isEqualTo("/shepard/api/collections");
    assertThat(LegacyV1DeprecationFilter.pathPattern("/shepard/api/collections/42/dataObjects/7"))
      .isEqualTo("/shepard/api/collections");
    assertThat(LegacyV1DeprecationFilter.pathPattern("/shepard/api/users/me"))
      .isEqualTo("/shepard/api/users");
    assertThat(LegacyV1DeprecationFilter.pathPattern("/shepard/api"))
      .isEqualTo("/shepard/api");
    assertThat(LegacyV1DeprecationFilter.pathPattern("/shepard/api/"))
      .isEqualTo("/shepard/api");
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private static RoutingContext mockContext(String path, String methodName) {
    HttpServerResponse response = mock(HttpServerResponse.class);
    when(response.putHeader(anyString(), anyString())).thenReturn(response);

    HttpServerRequest request = mock(HttpServerRequest.class);
    when(request.method()).thenReturn(HttpMethod.valueOf(methodName));

    RoutingContext rc = mock(RoutingContext.class);
    when(rc.normalizedPath()).thenReturn(path);
    when(rc.response()).thenReturn(response);
    when(rc.request()).thenReturn(request);
    return rc;
  }
}
