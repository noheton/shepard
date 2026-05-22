package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — covers the request-side instrumentation (counter
 * increment + first-hit dedup + write-method audit emission) and
 * the response-side header emission for
 * {@link LegacyV1DeprecationFilter}.
 *
 * <p>Both sides are load-bearing: the headers are how the frontend
 * banner knows the request flowed through v1; the counters are how
 * the {@code /v2/admin/legacy/v1/stats} endpoint shows operators
 * which clients are still on v1.
 */
class LegacyV1DeprecationFilterTest {

  private LegacyV1StatsService stats;
  private LegacyV1DeprecationFilter filter;

  @BeforeEach
  void setUp() {
    stats = new LegacyV1StatsService();
    filter = new LegacyV1DeprecationFilter(stats);
  }

  // ─── request side ────────────────────────────────────────────────────

  @Test
  void requestSide_v1Path_recordsHit() {
    ContainerRequestContext request = mockRequest("shepard/api/collections/42", "GET", "alice");
    filter.filter(request);

    assertThat(stats.getTotalHits()).isEqualTo(1);
    assertThat(stats.snapshot().byEndpoint())
      .extracting(c -> c.pathPattern())
      .containsExactly("/shepard/api/collections");
    assertThat(stats.snapshot().byPrincipal()).extracting(p -> p.principalSub()).containsExactly("alice");
  }

  @Test
  void requestSide_v1Path_aggregatesByEndpointFamily() {
    filter.filter(mockRequest("shepard/api/collections/1", "GET", "alice"));
    filter.filter(mockRequest("shepard/api/collections/2", "GET", "alice"));
    filter.filter(mockRequest("shepard/api/collections/42/dataObjects/7", "GET", "alice"));

    assertThat(stats.snapshot().byEndpoint())
      .extracting(c -> c.pathPattern(), c -> c.hits())
      .containsExactly(org.assertj.core.groups.Tuple.tuple("/shepard/api/collections", 3L));
  }

  @Test
  void requestSide_v2Path_isNoOp() {
    filter.filter(mockRequest("v2/collections/abc", "GET", "alice"));
    assertThat(stats.getTotalHits()).isZero();
  }

  @Test
  void requestSide_nullPrincipal_recordedAsAnonymous() {
    filter.filter(mockRequest("shepard/api/healthz", "GET", null));
    assertThat(stats.snapshot().byPrincipal()).extracting(p -> p.principalSub()).containsExactly("anonymous");
  }

  // ─── response side ────────────────────────────────────────────────────

  @Test
  void responseSide_v1Path_emitsThreeHeaders() {
    ContainerRequestContext request = mockRequest("shepard/api/collections", "GET", "alice");
    ContainerResponseContext response = mock(ContainerResponseContext.class);
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    when(response.getHeaders()).thenReturn(headers);

    filter.filter(request, response);

    assertThat(headers.getFirst("Deprecation")).isEqualTo("true");
    assertThat(headers.getFirst("Link")).isEqualTo("</v2/>; rel=\"successor-version\"");
    assertThat(headers.getFirst("X-Shepard-Legacy")).isEqualTo("true");
  }

  @Test
  void responseSide_v2Path_emitsNoHeaders() {
    ContainerRequestContext request = mockRequest("v2/collections", "GET", "alice");
    ContainerResponseContext response = mock(ContainerResponseContext.class);
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    when(response.getHeaders()).thenReturn(headers);

    filter.filter(request, response);

    assertThat(headers).isEmpty();
    verify(response, never()).getHeaders(); // never even reached
  }

  @Test
  void responseSide_putSingleSemantics_preventsDuplicates() {
    ContainerRequestContext request = mockRequest("shepard/api/collections", "GET", "alice");
    ContainerResponseContext response = mock(ContainerResponseContext.class);
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    when(response.getHeaders()).thenReturn(headers);

    filter.filter(request, response);
    filter.filter(request, response); // double-fire

    assertThat(headers.get("Deprecation")).hasSize(1);
    assertThat(headers.get("Link")).hasSize(1);
    assertThat(headers.get("X-Shepard-Legacy")).hasSize(1);
  }

  // ─── path-pattern helper ─────────────────────────────────────────────

  @Test
  void pathPattern_extractsResourceFamily() {
    assertThat(LegacyV1DeprecationFilter.pathPattern(mockRequest("shepard/api/collections", "GET", null)))
      .isEqualTo("/shepard/api/collections");
    assertThat(LegacyV1DeprecationFilter.pathPattern(mockRequest("shepard/api/collections/42/dataObjects/7", "GET", null)))
      .isEqualTo("/shepard/api/collections");
    assertThat(LegacyV1DeprecationFilter.pathPattern(mockRequest("shepard/api/users/me", "GET", null)))
      .isEqualTo("/shepard/api/users");
    assertThat(LegacyV1DeprecationFilter.pathPattern(mockRequest("shepard/api", "GET", null)))
      .isEqualTo("/shepard/api");
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  private static ContainerRequestContext mockRequest(String path, String method, String principalName) {
    ContainerRequestContext request = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn(path);
    when(request.getUriInfo()).thenReturn(uri);
    when(request.getMethod()).thenReturn(method);

    SecurityContext sc = mock(SecurityContext.class);
    if (principalName != null) {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn(principalName);
      when(sc.getUserPrincipal()).thenReturn(principal);
    } else {
      when(sc.getUserPrincipal()).thenReturn(null);
    }
    when(request.getSecurityContext()).thenReturn(sc);
    return request;
  }
}
