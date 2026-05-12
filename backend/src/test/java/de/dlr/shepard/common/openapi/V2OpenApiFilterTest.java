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

class V2OpenApiFilterTest {

  /**
   * Canonical fixture from the P4c spec: v2 filter keeps only the
   * {@code /v2/bar} entry — the upstream path is dropped, the
   * platform path is dropped.
   */
  @Test
  void keepsOnlyTheV2Path() {
    OpenAPI openAPI = fixture("/shepard/api/foo", "/v2/bar", "/healthz");

    new V2OpenApiFilter().filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertEquals(1, after.size());
    assertTrue(after.containsKey("/v2/bar"));
    assertFalse(after.containsKey("/shepard/api/foo"));
    assertFalse(after.containsKey("/healthz"));
  }

  @Test
  void v2PrefixIsExactNotSubstring() {
    // A hypothetical /v2foo must NOT be classified v2 (would be a
    // resource-name collision rather than a shelf member).
    OpenAPI openAPI = fixture("/v2/bundles", "/v2foo", "/v2");

    new V2OpenApiFilter().filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertTrue(after.containsKey("/v2/bundles"));
    assertTrue(after.containsKey("/v2"));
    assertFalse(after.containsKey("/v2foo"));
  }

  @Test
  void emptyResultWhenNoV2Paths() {
    OpenAPI openAPI = fixture("/shepard/api/foo", "/collections", "/healthz");

    new V2OpenApiFilter().filterOpenAPI(openAPI);

    assertTrue(openAPI.getPaths().getPathItems().isEmpty());
  }

  @Test
  void nullPathsIsHandled() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    // No paths set — must not NPE.
    new V2OpenApiFilter().filterOpenAPI(openAPI);
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
