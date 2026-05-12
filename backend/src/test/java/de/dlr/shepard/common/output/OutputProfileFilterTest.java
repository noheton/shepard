package de.dlr.shepard.common.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class OutputProfileFilterTest {

  @Mock
  ContainerRequestContext request;

  @Mock
  UriInfo uriInfo;

  OutputProfileFilter filter;
  OutputProfileResolver resolver;
  MultivaluedMap<String, String> queryParams;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new OutputProfileFilter();
    resolver = new OutputProfileResolver();
    filter.resolver = resolver;
    queryParams = new MultivaluedHashMap<>();
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
  }

  private void mockPath(String absoluteAppPath) {
    // RequestPathHelper#applicationPath uses requestContext.getUriInfo().getPath()
    // which is the application-relative path; with quarkus.http.root-path=/,
    // RequestPathHelper just leading-slashes it.
    when(uriInfo.getPath()).thenReturn(absoluteAppPath.startsWith("/") ? absoluteAppPath.substring(1) : absoluteAppPath);
  }

  @Test
  void v2RequestWithoutProfileLeavesDefault() throws IOException {
    mockPath("/v2/provenance/activities");
    filter.filter(request);
    assertEquals(OutputProfile.ALL, resolver.getProfile());
  }

  @Test
  void v2RequestWithMetadataProfileSetsResolver() throws IOException {
    mockPath("/v2/provenance/activities");
    queryParams.putSingle("profile", "metadata");
    filter.filter(request);
    assertEquals(OutputProfile.METADATA, resolver.getProfile());
  }

  @Test
  void v2RequestWithRelationsProfileSetsResolver() throws IOException {
    mockPath("/v2/provenance/activities");
    queryParams.putSingle("profile", "relations");
    filter.filter(request);
    assertEquals(OutputProfile.RELATIONS, resolver.getProfile());
  }

  @Test
  void v2RequestWithUnknownProfileThrowsBadRequest() {
    mockPath("/v2/provenance/activities");
    queryParams.putSingle("profile", "nonsense");
    WebApplicationException ex = assertThrows(WebApplicationException.class, () -> filter.filter(request));
    assertEquals(400, ex.getResponse().getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) ex.getResponse().getEntity();
    assertTrue(body.get("detail").toString().contains("metadata"));
    assertTrue(body.get("detail").toString().contains("relations"));
    assertTrue(body.get("detail").toString().contains("all"));
  }

  @Test
  void shepardApiRequestIgnoresProfile() throws IOException {
    // Legacy /shepard/api/ endpoints stay byte-frozen per CLAUDE.md.
    mockPath("/shepard/api/collections");
    queryParams.putSingle("profile", "metadata");
    filter.filter(request);
    assertEquals(OutputProfile.DEFAULT, resolver.getProfile());
  }

  @Test
  void caseInsensitiveProfileValue() throws IOException {
    mockPath("/v2/provenance/activities");
    queryParams.putSingle("profile", "METADATA");
    filter.filter(request);
    assertEquals(OutputProfile.METADATA, resolver.getProfile());
  }
}
