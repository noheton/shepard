package de.dlr.shepard.common.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A5 — unit tests for {@link DisabledNamespaceOasFilter}.
 *
 * <p>Verifies the OpenAPI strip operates purely on the injected disabled-prefix list,
 * independent of any CDI / registry boot.
 */
class DisabledNamespaceOasFilterTest {

  @Test
  void stripsPathsUnderDisabledPrefix() {
    OpenAPI openAPI = fixture("/v2/aas/shells", "/v2/aas/.well-known/aas-server", "/v2/collections");

    new DisabledNamespaceOasFilter(List.of("/v2/aas")).filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertEquals(1, after.size());
    assertTrue(after.containsKey("/v2/collections"));
    assertFalse(after.containsKey("/v2/aas/shells"));
    assertFalse(after.containsKey("/v2/aas/.well-known/aas-server"));
  }

  @Test
  void emptyDisabledList_isNoOp() {
    OpenAPI openAPI = fixture("/v2/aas/shells", "/v2/collections");

    new DisabledNamespaceOasFilter(List.of()).filterOpenAPI(openAPI);

    assertEquals(2, openAPI.getPaths().getPathItems().size());
  }

  @Test
  void nullDisabledList_isNoOp() {
    OpenAPI openAPI = fixture("/v2/aas/shells");

    new DisabledNamespaceOasFilter(null).filterOpenAPI(openAPI);

    assertEquals(1, openAPI.getPaths().getPathItems().size());
  }

  @Test
  void prefixMatchIsStructuralNotSubstring() {
    OpenAPI openAPI = fixture("/v2/aas/shells", "/v2/aaszzz", "/v2/aas");

    new DisabledNamespaceOasFilter(List.of("/v2/aas")).filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertTrue(after.containsKey("/v2/aaszzz"));
    assertFalse(after.containsKey("/v2/aas/shells"));
    assertFalse(after.containsKey("/v2/aas"));
  }

  @Test
  void multipleDisabledPrefixes_allStripped() {
    OpenAPI openAPI = fixture("/v2/aas/shells", "/v2/jupyter/config", "/v2/collections");

    new DisabledNamespaceOasFilter(List.of("/v2/aas", "/v2/jupyter")).filterOpenAPI(openAPI);

    Map<String, PathItem> after = openAPI.getPaths().getPathItems();
    assertEquals(1, after.size());
    assertTrue(after.containsKey("/v2/collections"));
  }

  @Test
  void nullPaths_isHandled() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    new DisabledNamespaceOasFilter(List.of("/v2/aas")).filterOpenAPI(openAPI);
  }

  @Test
  void isUnderDisabledPrefix_helper() {
    DisabledNamespaceOasFilter filter = new DisabledNamespaceOasFilter(List.of("/v2/aas"));
    assertTrue(filter.isUnderDisabledPrefix("/v2/aas"));
    assertTrue(filter.isUnderDisabledPrefix("/v2/aas/shells"));
    assertFalse(filter.isUnderDisabledPrefix("/v2/aaszzz"));
    assertFalse(filter.isUnderDisabledPrefix("/v2/collections"));
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
