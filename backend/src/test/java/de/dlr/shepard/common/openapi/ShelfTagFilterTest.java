package de.dlr.shepard.common.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.junit.jupiter.api.Test;

class ShelfTagFilterTest {

  @Test
  void tagsV1AndV2OperationsWithMatchingPrefix() {
    OpenAPI openAPI = fixtureWithGet(Map.of(
      "/collections", "list-collections",
      "/v2/collections/{appId}/snapshots", "list-snapshots",
      "/healthz", "health"
    ));

    new ShelfTagFilter().filterOpenAPI(openAPI);

    Map<String, PathItem> paths = openAPI.getPaths().getPathItems();

    assertTrue(paths.get("/collections").getGET().getSummary().startsWith(ShelfTagFilter.V1_PREFIX));
    assertEquals("v1", paths.get("/collections").getExtensions().get(ShelfTagFilter.EXT_SHELF));

    assertTrue(paths.get("/v2/collections/{appId}/snapshots").getGET().getSummary().startsWith(ShelfTagFilter.V2_PREFIX));
    assertEquals("v2", paths.get("/v2/collections/{appId}/snapshots").getExtensions().get(ShelfTagFilter.EXT_SHELF));

    assertTrue(paths.get("/healthz").getGET().getSummary().startsWith(ShelfTagFilter.PLATFORM_PREFIX));
    assertEquals("platform", paths.get("/healthz").getExtensions().get(ShelfTagFilter.EXT_SHELF));
  }

  @Test
  void leavesAlreadyTaggedSummaryAlone() {
    // Defensive: the filter is idempotent so a second build pass (hot
    // reload, etc.) doesn't produce "[v1] [v1] list collections".
    OpenAPI openAPI = OASFactory.createOpenAPI();
    Paths model = OASFactory.createPaths();
    PathItem item = OASFactory.createPathItem();
    Operation op = OASFactory.createOperation().summary("[v1] already tagged").operationId("op");
    item.setGET(op);
    Map<String, PathItem> items = new LinkedHashMap<>();
    items.put("/collections", item);
    model.setPathItems(items);
    openAPI.setPaths(model);

    new ShelfTagFilter().filterOpenAPI(openAPI);

    assertEquals("[v1] already tagged", item.getGET().getSummary());
  }

  @Test
  void synthesisesSummaryFromOperationIdWhenMissing() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    Paths model = OASFactory.createPaths();
    PathItem item = OASFactory.createPathItem();
    Operation op = OASFactory.createOperation().operationId("getAllSnapshots");
    item.setGET(op);
    Map<String, PathItem> items = new LinkedHashMap<>();
    items.put("/v2/collections/{appId}/snapshots", item);
    model.setPathItems(items);
    openAPI.setPaths(model);

    new ShelfTagFilter().filterOpenAPI(openAPI);

    assertEquals("[v2] getAllSnapshots", item.getGET().getSummary());
  }

  @Test
  void nullPathsDoesNotThrow() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    new ShelfTagFilter().filterOpenAPI(openAPI);
    assertNull(openAPI.getPaths());
  }

  @Test
  void coversAllHttpVerbsOnAPath() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    Paths model = OASFactory.createPaths();
    PathItem item = OASFactory.createPathItem();
    item.setGET(OASFactory.createOperation().summary("read"));
    item.setPOST(OASFactory.createOperation().summary("create"));
    item.setPATCH(OASFactory.createOperation().summary("merge"));
    item.setDELETE(OASFactory.createOperation().summary("remove"));
    Map<String, PathItem> items = new LinkedHashMap<>();
    items.put("/v2/collections/{appId}/snapshots", item);
    model.setPathItems(items);
    openAPI.setPaths(model);

    new ShelfTagFilter().filterOpenAPI(openAPI);

    assertNotNull(item.getGET()); assertTrue(item.getGET().getSummary().startsWith("[v2] "));
    assertNotNull(item.getPOST()); assertTrue(item.getPOST().getSummary().startsWith("[v2] "));
    assertNotNull(item.getPATCH()); assertTrue(item.getPATCH().getSummary().startsWith("[v2] "));
    assertNotNull(item.getDELETE()); assertTrue(item.getDELETE().getSummary().startsWith("[v2] "));
  }

  private static OpenAPI fixtureWithGet(Map<String, String> pathToSummary) {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    Paths model = OASFactory.createPaths();
    Map<String, PathItem> items = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : pathToSummary.entrySet()) {
      PathItem item = OASFactory.createPathItem();
      item.setGET(OASFactory.createOperation().summary(e.getValue()));
      items.put(e.getKey(), item);
    }
    model.setPathItems(items);
    openAPI.setPaths(model);
    return openAPI;
  }
}
