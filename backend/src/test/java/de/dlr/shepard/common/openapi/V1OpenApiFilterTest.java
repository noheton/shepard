package de.dlr.shepard.common.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.junit.jupiter.api.Test;

class V1OpenApiFilterTest {

  /**
   * The canonical fixture from the P4c spec: three paths, one per
   * shelf-class. V1 must keep {@code /shepard/api/foo} only — the
   * v2 path is dropped, the platform path is dropped.
   */
  @Test
  void keepsOnlyTheUpstreamApiPath() {
    OpenAPI openAPI = fixture("/shepard/api/foo", "/v2/bar", "/healthz");

    new V1OpenApiFilter().filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertEquals(1, after.size());
    assertTrue(after.containsKey("/shepard/api/foo"));
    assertFalse(after.containsKey("/v2/bar"));
    assertFalse(after.containsKey("/healthz"));
  }

  /**
   * After build-time ApiPathFilter has stripped the {@code /shepard/api}
   * prefix the model holds paths like {@code /collections}. The v1
   * filter must keep those too — anything not under {@code /v2/} and
   * not a platform path is "the v1 shelf".
   */
  @Test
  void keepsPostStripPathsAsV1() {
    OpenAPI openAPI = fixture("/collections", "/users/me", "/v2/bundles", "/healthz", "/openapi.json");

    new V1OpenApiFilter().filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertEquals(2, after.size());
    assertTrue(after.containsKey("/collections"));
    assertTrue(after.containsKey("/users/me"));
  }

  @Test
  void platformPrefixesAreDroppedFromV1() {
    OpenAPI openAPI = fixture("/healthz", "/healthz/ready", "/openapi", "/openapi.json", "/metrics");

    new V1OpenApiFilter().filterOpenAPI(openAPI);

    assertTrue(openAPI.getPaths().getPathItems().isEmpty());
  }

  @Test
  void healthzPrefixDoesNotEatHealthzlikeApiPath() {
    // Defensive: a hypothetical /shepard/api/healthzy must not be
    // misclassified as a platform path because of /healthz prefix-like
    // matching. The membership classifier requires either exact match
    // or `prefix + "/"`.
    OpenAPI openAPI = fixture("/shepard/api/healthzy");

    new V1OpenApiFilter().filterOpenAPI(openAPI);

    assertTrue(openAPI.getPaths().getPathItems().containsKey("/shepard/api/healthzy"));
  }

  @Test
  void nullPathsIsHandled() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    // No paths set — must not NPE.
    new V1OpenApiFilter().filterOpenAPI(openAPI);
  }

  private static OpenAPI fixture(String... paths) {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    Paths model = OASFactory.createPaths();
    Map<String, PathItem> items = new LinkedHashMap<>();
    for (String p : paths) {
      items.put(p, OASFactory.createPathItem());
    }
    model.setPathItems(items);
    openAPI.setPaths(model);
    return openAPI;
  }
}
