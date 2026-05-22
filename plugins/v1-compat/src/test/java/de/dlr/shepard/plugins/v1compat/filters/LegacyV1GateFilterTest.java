package de.dlr.shepard.plugins.v1compat.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * V1COMPAT.0 — load-bearing tests for the 410 gate filter. The
 * gate is what makes the operator's runtime flip of
 * {@code :LegacyV1Config.enabled=false} observable to callers; if
 * the wire shape is wrong or the path-detection is wrong, the
 * operator's gesture is silent and the deprecation lever is
 * effectively broken.
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
  void v1Path_enabledTrue_filterIsNoOp() {
    when(service.isEnabled()).thenReturn(true);

    ContainerRequestContext request = mockRequest("shepard/api/collections");
    filter.filter(request);

    verify(request, never()).abortWith(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void v1Path_enabledFalse_emits410WithProblemJsonBody() {
    when(service.isEnabled()).thenReturn(false);

    ContainerRequestContext request = mockRequest("shepard/api/collections/42");
    filter.filter(request);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(request).abortWith(captor.capture());

    Response response = captor.getValue();
    assertThat(response.getStatus()).isEqualTo(Response.Status.GONE.getStatusCode());
    assertThat(response.getHeaderString("Content-Type")).isEqualTo(Constants.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaderString("Deprecation")).isEqualTo("true");
    assertThat(response.getHeaderString("Link")).isEqualTo("</v2/>; rel=\"successor-version\"");
    assertThat(response.getHeaderString("X-Shepard-Legacy")).isEqualTo("true");

    Object entity = response.getEntity();
    assertThat(entity).isInstanceOf(ProblemJson.class);
    ProblemJson body = (ProblemJson) entity;
    assertThat(body.type()).isEqualTo(LegacyV1GateFilter.PROBLEM_TYPE_V1_DISABLED);
    assertThat(body.title()).isEqualTo("Legacy v1 surface disabled");
    assertThat(body.status()).isEqualTo(410);
    assertThat(body.detail())
      .contains("legacy /shepard/api/... surface is disabled")
      .contains("Migrate to /v2/");
    assertThat(body.instance()).isEqualTo("/shepard/api/collections/42");
  }

  @Test
  void v2Path_filterIsNoOp_regardlessOfEnabled() {
    when(service.isEnabled()).thenReturn(false);

    ContainerRequestContext request = mockRequest("v2/collections/abc");
    filter.filter(request);

    verify(request, never()).abortWith(org.mockito.ArgumentMatchers.any());
    // The service should not even be consulted for non-v1 paths
    verify(service, never()).isEnabled();
  }

  @Test
  void healthzPath_filterIsNoOp() {
    when(service.isEnabled()).thenReturn(false);

    ContainerRequestContext request = mockRequest("healthz");
    filter.filter(request);

    verify(request, never()).abortWith(org.mockito.ArgumentMatchers.any());
    verify(service, never()).isEnabled();
  }

  @Test
  void barePrefix_treatedAsV1Path() {
    when(service.isEnabled()).thenReturn(false);

    // Edge case: an operator hitting the bare "/shepard/api" without
    // a trailing slash — still an attempt against the v1 surface.
    ContainerRequestContext request = mockRequest("shepard/api");
    filter.filter(request);

    verify(request).abortWith(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unrelatedPrefix_treatedAsNonV1Path() {
    when(service.isEnabled()).thenReturn(false);

    // A path that *contains* "shepard/api" but doesn't start with it
    // must NOT be 410'd — defends against an accidental prefix match.
    ContainerRequestContext request = mockRequest("v2/something/shepard/api/thing");
    filter.filter(request);

    verify(request, never()).abortWith(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void isV1Path_nullRequest_returnsFalse() {
    assertThat(LegacyV1GateFilter.isV1Path(null)).isFalse();
  }

  @Test
  void isV1Path_nullUriInfo_returnsFalse() {
    ContainerRequestContext request = mock(ContainerRequestContext.class);
    when(request.getUriInfo()).thenReturn(null);
    assertThat(LegacyV1GateFilter.isV1Path(request)).isFalse();
  }

  @Test
  void isV1Path_nullPath_returnsFalse() {
    ContainerRequestContext request = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn(null);
    when(request.getUriInfo()).thenReturn(uri);
    assertThat(LegacyV1GateFilter.isV1Path(request)).isFalse();
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────────────

  private static ContainerRequestContext mockRequest(String path) {
    ContainerRequestContext request = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn(path);
    when(request.getUriInfo()).thenReturn(uri);
    return request;
  }
}
