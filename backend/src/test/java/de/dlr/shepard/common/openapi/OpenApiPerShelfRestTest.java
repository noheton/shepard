package de.dlr.shepard.common.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.smallrye.openapi.api.OpenApiDocument;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit-level coverage for {@link OpenApiPerShelfRest}. The class
 * pulls from the global {@code OpenApiDocument.INSTANCE} singleton —
 * the test populates a hand-built doc, calls the REST methods
 * directly, and parses the response body. No Quarkus boot.
 */
class OpenApiPerShelfRestTest {

  private OpenApiPerShelfRest resource;
  private boolean openApiDocPrePopulated;

  @BeforeEach
  void setUp() {
    // The smallrye-openapi singleton lives across tests. Reset before
    // and after to keep the test deterministic, then push the fixture.
    openApiDocPrePopulated = OpenApiDocument.INSTANCE.isSet();
    OpenApiDocument.INSTANCE.reset();
    OpenApiDocument.INSTANCE.set(fixture());
    resource = new OpenApiPerShelfRest();
  }

  @AfterEach
  void tearDown() {
    OpenApiDocument.INSTANCE.reset();
    // If the surrounding test environment had populated the singleton,
    // we cannot restore it from a snapshot — but every Quarkus test
    // that exercises OpenAPI re-populates it during boot, so a reset
    // here is safe for the test profile.
    if (openApiDocPrePopulated) {
      // No-op; documented for clarity.
    }
  }

  @Test
  void classCarriesPermitAllGate() {
    assertNotNull(
      OpenApiPerShelfRest.class.getAnnotation(PermitAll.class),
      "OpenApiPerShelfRest must be @PermitAll — OpenAPI specs are public"
    );
  }

  @Test
  void classCarriesExpectedPath() {
    Path path = OpenApiPerShelfRest.class.getAnnotation(Path.class);
    assertNotNull(path);
    assertEquals("/shepard/doc/openapi", path.value());
  }

  @Test
  void v1ShelfContainsApiPathsAndNoV2Paths() throws Exception {
    Response r = resource.getV1Shelf(null);

    assertEquals(200, r.getStatus());
    assertEquals(MediaType.APPLICATION_JSON, r.getMediaType().toString());
    JsonNode doc = parseJson(r);
    JsonNode paths = doc.path("paths");
    assertTrue(paths.has("/shepard/api/collections"), "v1 doc must keep upstream API paths");
    assertTrue(paths.has("/collections"), "v1 doc must keep post-strip API paths");
    assertFalse(paths.has("/v2/bundles"), "v1 doc must NOT include /v2/... paths");
    assertFalse(paths.has("/healthz"), "v1 doc must NOT include platform paths");
  }

  @Test
  void v2ShelfContainsOnlyV2Paths() throws Exception {
    Response r = resource.getV2Shelf(null);

    assertEquals(200, r.getStatus());
    assertEquals(MediaType.APPLICATION_JSON, r.getMediaType().toString());
    JsonNode doc = parseJson(r);
    JsonNode paths = doc.path("paths");
    assertTrue(paths.has("/v2/bundles"), "v2 doc must keep /v2/... paths");
    assertFalse(paths.has("/shepard/api/collections"), "v2 doc must NOT include raw upstream paths");
    assertFalse(paths.has("/collections"), "v2 doc must NOT include post-strip upstream paths");
    assertFalse(paths.has("/healthz"), "v2 doc must NOT include platform paths");
  }

  @Test
  void yamlFormatReturnsYamlMediaType() throws Exception {
    Response rV1 = resource.getV1Shelf("yaml");
    Response rV2 = resource.getV2Shelf("yaml");

    assertEquals(200, rV1.getStatus());
    assertEquals(200, rV2.getStatus());
    assertEquals("application/yaml", rV1.getMediaType().toString());
    assertEquals("application/yaml", rV2.getMediaType().toString());
    // Confirm YAML is well-formed and carries the expected shelf.
    JsonNode v1 = new YAMLMapper().readTree((String) rV1.getEntity());
    JsonNode v2 = new YAMLMapper().readTree((String) rV2.getEntity());
    assertTrue(v1.path("paths").has("/shepard/api/collections"));
    assertTrue(v2.path("paths").has("/v2/bundles"));
  }

  @Test
  void yamlFormatIsCaseInsensitive() throws Exception {
    Response r = resource.getV1Shelf("YAML");

    assertEquals("application/yaml", r.getMediaType().toString());
  }

  @Test
  void unsetOpenApiDocumentRaises500() {
    OpenApiDocument.INSTANCE.reset();

    assertThrows(InternalServerErrorException.class, () -> resource.getV1Shelf(null));
    assertThrows(InternalServerErrorException.class, () -> resource.getV2Shelf(null));
  }

  @Test
  void filteringDoesNotMutateSingleton() throws Exception {
    // Belt-and-braces: pulling v1 then v2 must each receive an
    // independent clone — the live singleton must keep all paths.
    resource.getV1Shelf(null);
    resource.getV2Shelf(null);

    OpenAPI live = OpenApiDocument.INSTANCE.get();
    Map<String, PathItem> paths = live.getPaths().getPathItems();
    assertTrue(paths.containsKey("/shepard/api/collections"));
    assertTrue(paths.containsKey("/collections"));
    assertTrue(paths.containsKey("/v2/bundles"));
    assertTrue(paths.containsKey("/healthz"));
  }

  private static JsonNode parseJson(Response r) throws Exception {
    return new ObjectMapper().readTree((String) r.getEntity());
  }

  private static OpenAPI fixture() {
    OpenAPI openAPI = OASFactory.createOpenAPI();
    openAPI.info(OASFactory.createInfo().title("shepard test").version("1.2.3"));
    Paths model = OASFactory.createPaths();
    Map<String, PathItem> items = new LinkedHashMap<>();
    // Raw upstream shape (e.g. ahead of ApiPathFilter): kept by v1
    items.put("/shepard/api/collections", OASFactory.createPathItem());
    // Stripped upstream shape (post-ApiPathFilter at runtime): also v1
    items.put("/collections", OASFactory.createPathItem());
    // v2 shelf
    items.put("/v2/bundles", OASFactory.createPathItem());
    // Platform: neither shelf
    items.put("/healthz", OASFactory.createPathItem());
    model.setPathItems(items);
    openAPI.setPaths(model);
    return openAPI;
  }
}
