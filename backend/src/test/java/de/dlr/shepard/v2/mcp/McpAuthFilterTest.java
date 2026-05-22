package de.dlr.shepard.v2.mcp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.ApiKeyAuthService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JwtTokenAuthService;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpAuthFilterTest {

  private JwtTokenAuthService jwtTokenAuthService;
  private ApiKeyAuthService apiKeyAuthService;
  private AuthenticationContext authContext;
  private Instance<AuthenticationContext> authContextInstance;
  private McpAuthFilter filter;

  private RoutingContext rc;
  private HttpServerRequest req;
  private HttpServerResponse resp;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    jwtTokenAuthService = mock(JwtTokenAuthService.class);
    apiKeyAuthService = mock(ApiKeyAuthService.class);
    authContext = mock(AuthenticationContext.class);
    authContextInstance = mock(Instance.class);
    when(authContextInstance.get()).thenReturn(authContext);

    filter = new McpAuthFilter();
    filter.jwtTokenAuthService = jwtTokenAuthService;
    filter.apiKeyAuthService = apiKeyAuthService;
    filter.authenticationContext = authContextInstance;

    rc = mock(RoutingContext.class);
    req = mock(HttpServerRequest.class);
    resp = mock(HttpServerResponse.class);
    when(rc.request()).thenReturn(req);
    when(rc.response()).thenReturn(resp);
    when(resp.setStatusCode(org.mockito.ArgumentMatchers.anyInt())).thenReturn(resp);
    when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
    when(req.method()).thenReturn(HttpMethod.POST);
  }

  @Test
  void nonMcpPath_passesThrough() {
    when(rc.normalizedPath()).thenReturn("/shepard/api/collections");

    filter.handle(rc);

    verify(rc).next();
    verify(jwtTokenAuthService, never()).parseBearerToken(anyString());
    verify(resp, never()).setStatusCode(org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void prefixLookalike_doesNotMatch() {
    // /v2/mcp-something must NOT match the /v2/mcp prefix.
    when(rc.normalizedPath()).thenReturn("/v2/mcp-foo");

    filter.handle(rc);

    verify(rc).next();
    verify(jwtTokenAuthService, never()).parseBearerToken(anyString());
  }

  @Test
  void optionsPreflight_passesThroughWithoutAuth() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/sse");
    when(req.method()).thenReturn(HttpMethod.OPTIONS);

    filter.handle(rc);

    verify(rc).next();
    verify(jwtTokenAuthService, never()).parseBearerToken(anyString());
  }

  @Test
  void missingAuthorizationHeader_returns401() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/sse");
    when(req.getHeader("Authorization")).thenReturn(null);

    filter.handle(rc);

    verify(resp).setStatusCode(401);
    verify(resp).end(org.mockito.ArgumentMatchers.contains("AuthenticationException"));
    verify(rc, never()).next();
  }

  @Test
  void nonBearerScheme_returns401() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/sse");
    when(req.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

    filter.handle(rc);

    verify(resp).setStatusCode(401);
    verify(jwtTokenAuthService, never()).parseBearerToken(anyString());
  }

  @Test
  void invalidToken_returns401() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp");
    when(req.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
    when(jwtTokenAuthService.parseBearerToken("Bearer bad.token.here")).thenReturn(null);

    filter.handle(rc);

    verify(resp).setStatusCode(401);
    verify(rc, never()).next();
  }

  @Test
  void validToken_passesThrough_andSetsAuthContext() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/messages");
    when(req.getHeader("Authorization")).thenReturn("Bearer good.token");

    JWTPrincipal principal = new JWTPrincipal("aud", "azp", "alice", "kid", Set.of("users"));
    when(jwtTokenAuthService.parseBearerToken("Bearer good.token")).thenReturn(principal);

    filter.handle(rc);

    verify(rc).put(McpAuthFilter.PRINCIPAL_CONTEXT_KEY, principal);
    verify(authContext, atLeastOnce()).setPrincipal(principal);
    verify(rc).next();
    verify(resp, never()).setStatusCode(401);
  }

  @Test
  void exactPrefix_isAuthChecked() {
    // /v2/mcp on its own must require auth (the streamable HTTP endpoint).
    when(rc.normalizedPath()).thenReturn("/v2/mcp");
    when(req.getHeader("Authorization")).thenReturn(null);

    filter.handle(rc);

    verify(resp).setStatusCode(401);
  }

  @Test
  void bearerApiKey_fallsThroughToApiKeyValidator() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp");
    String jws = "header.payload.sig";
    when(req.getHeader("Authorization")).thenReturn("Bearer " + jws);
    when(jwtTokenAuthService.parseBearerToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

    JWTPrincipal apiPrincipal = new JWTPrincipal("aud", "azp", "bob", "kid", Set.of("users"));
    when(apiKeyAuthService.parseApiKey(jws)).thenReturn(apiPrincipal);

    filter.handle(rc);

    verify(rc).put(McpAuthFilter.PRINCIPAL_CONTEXT_KEY, apiPrincipal);
    verify(authContext).setPrincipal(apiPrincipal);
    verify(rc).next();
  }

  @Test
  void xApiKeyHeader_validates() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/sse");
    String jws = "h.p.s";
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getHeader("X-API-KEY")).thenReturn(jws);
    JWTPrincipal p = new JWTPrincipal("aud", "azp", "carol", "kid", Set.of());
    when(apiKeyAuthService.parseApiKey(jws)).thenReturn(p);

    filter.handle(rc);

    verify(rc).put(McpAuthFilter.PRINCIPAL_CONTEXT_KEY, p);
    verify(rc).next();
    verify(resp, org.mockito.Mockito.never()).setStatusCode(org.mockito.ArgumentMatchers.eq(401));
  }

  @Test
  void expiredApiKey_returns401WithWwwAuthenticate() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/sse");
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getHeader("X-API-KEY")).thenReturn("expired.token.here");
    when(apiKeyAuthService.parseApiKey(org.mockito.ArgumentMatchers.anyString()))
      .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

    filter.handle(rc);

    verify(resp).setStatusCode(401);
    verify(resp).putHeader(org.mockito.ArgumentMatchers.eq("WWW-Authenticate"),
      org.mockito.ArgumentMatchers.contains("expired"));
  }

  @Test
  void bearerOidcTokenStillWorks_andDoesNotCallApiKeyValidator() {
    when(rc.normalizedPath()).thenReturn("/v2/mcp/messages");
    when(req.getHeader("Authorization")).thenReturn("Bearer oidc.jwt.token");
    JWTPrincipal p = new JWTPrincipal("aud", "azp", "alice", "kid", Set.of());
    when(jwtTokenAuthService.parseBearerToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(p);

    filter.handle(rc);

    verify(rc).put(McpAuthFilter.PRINCIPAL_CONTEXT_KEY, p);
    verify(apiKeyAuthService, org.mockito.Mockito.never())
      .parseApiKey(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void cdiFailure_doesNotMaskAuthSuccess() {
    // If AuthenticationContext lookup throws (CDI scope edge case),
    // the request still proceeds — principal is on routing context.
    when(rc.normalizedPath()).thenReturn("/v2/mcp");
    when(req.getHeader("Authorization")).thenReturn("Bearer good.token");
    JWTPrincipal principal = new JWTPrincipal("aud", "azp", "alice", "kid", Set.of());
    when(jwtTokenAuthService.parseBearerToken(anyString())).thenReturn(principal);
    when(authContextInstance.get()).thenThrow(new RuntimeException("ContextNotActive"));

    filter.handle(rc);

    ArgumentCaptor<Object> stash = ArgumentCaptor.forClass(Object.class);
    verify(rc).put(org.mockito.ArgumentMatchers.eq(McpAuthFilter.PRINCIPAL_CONTEXT_KEY), stash.capture());
    org.junit.jupiter.api.Assertions.assertSame(principal, stash.getValue());
    verify(rc).next();
  }
}
