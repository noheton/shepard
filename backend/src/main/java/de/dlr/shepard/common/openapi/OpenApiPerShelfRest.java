package de.dlr.shepard.common.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * P4c — exposes the v1 / v2 OpenAPI shelves as derived endpoints over
 * the combined {@code /shepard/doc/openapi.json} document.
 *
 * <p>The combined view served by the Quarkus smallrye-openapi
 * extension at {@code /shepard/doc/openapi.json} remains the canonical
 * doc; upstream-built clients pointing at it keep working. The two
 * endpoints here are <em>additive</em>:
 *
 * <ul>
 *   <li>{@code GET /shepard/doc/openapi/v1.json} — only the upstream-
 *       compatible {@code /shepard/api/...} paths
 *       (post-{@link de.dlr.shepard.common.filters.ApiPathFilter} the
 *       paths render without the {@code /shepard/api} prefix, mirroring
 *       upstream's wire shape).</li>
 *   <li>{@code GET /shepard/doc/openapi/v2.json} — only the fork's
 *       {@code /v2/...} development surface.</li>
 * </ul>
 *
 * <p>Both endpoints honour {@code ?format=yaml} for YAML output
 * ({@code application/yaml}); default is JSON ({@code application/json}).
 * Both are public ({@link PermitAll} on the class) and listed in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} so the
 * JWT filter lets them through pre-auth — OpenAPI specs are public,
 * mirroring the combined {@code /openapi.json}.
 *
 * <p><b>Implementation note (post-Quarkus 3.x).</b> Earlier versions
 * reused the smallrye {@code OpenApiDocument.INSTANCE} singleton via
 * {@code FilterUtil.applyFilter}. Under Quarkus 3.x that singleton is
 * cleared after the build-time pass — the runtime model lives in a
 * separate smallrye-internal holder we have no stable access to from
 * application code. The shelf endpoints went 500 ("OpenAPI document is
 * not yet initialised.") whenever called, blocking Kiota client-gen
 * and any LLM consumer reading the per-shelf docs.
 *
 * <p>Current shape: fetch the combined JSON from the SmallRye-served
 * path via an in-process loopback, then JSON-tree-filter the
 * {@code paths} map keys via {@link OpenApiShelfMembership}. No
 * private-API surface. One extra loopback HTTP roundtrip per
 * discovery call — these endpoints are admin/client-gen, not hot
 * path. {@link V1OpenApiFilter} / {@link V2OpenApiFilter} kept for
 * unit tests of the shelf membership predicate but no longer wired
 * here.
 *
 * <p>Lands per <code>aidocs/16</code> P4c; rationale and contract are
 * documented there.
 */
@Path("/shepard/doc/openapi")
@PermitAll
@ApplicationScoped
public class OpenApiPerShelfRest {

  static final String V1_PATH = "/shepard/doc/openapi/v1.json";
  static final String V2_PATH = "/shepard/doc/openapi/v2.json";

  private static final String COMBINED_LOOPBACK_URL = "http://localhost:8080/shepard/doc/openapi.json";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final YAMLMapper YAML = new YAMLMapper();

  @GET
  @Path("/v1.json")
  @Tag(name = "openapi-per-shelf")
  @Operation(description = "OpenAPI document for the upstream-compatible /shepard/api/... shelf only.")
  public Response getV1Shelf(@QueryParam("format") String format) {
    return serialise(OpenApiShelfMembership::isV1Path, format);
  }

  @GET
  @Path("/v2.json")
  @Tag(name = "openapi-per-shelf")
  @Operation(description = "OpenAPI document for the /v2/... development shelf only.")
  public Response getV2Shelf(@QueryParam("format") String format) {
    return serialise(OpenApiShelfMembership::isV2Path, format);
  }

  private Response serialise(Predicate<String> pathPredicate, String format) {
    JsonNode combined = fetchCombinedDocument();
    JsonNode filtered = filterPaths(combined, pathPredicate);
    boolean yaml = "yaml".equalsIgnoreCase(format);
    try {
      byte[] body = yaml ? YAML.writeValueAsBytes(filtered) : JSON.writeValueAsBytes(filtered);
      String mediaType = yaml ? "application/yaml" : MediaType.APPLICATION_JSON;
      return Response.ok(body).type(mediaType).build();
    } catch (Exception ex) {
      throw new InternalServerErrorException("Failed to serialise OpenAPI document: " + ex.getMessage(), ex);
    }
  }

  /**
   * Filter the {@code paths} map of the combined OpenAPI document to
   * only the keys matching {@code pathPredicate}. All other top-level
   * fields (info, components, servers, etc.) are kept as-is.
   */
  private JsonNode filterPaths(JsonNode combined, Predicate<String> pathPredicate) {
    if (!(combined instanceof ObjectNode root)) return combined;
    JsonNode paths = root.get("paths");
    if (!(paths instanceof ObjectNode pathsObj)) return root;
    Map<String, JsonNode> kept = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> it = pathsObj.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      if (pathPredicate.test(e.getKey())) {
        kept.put(e.getKey(), e.getValue());
      }
    }
    ObjectNode newPaths = JSON.createObjectNode();
    kept.forEach(newPaths::set);
    root.set("paths", newPaths);
    return root;
  }

  /**
   * Fetch the combined OpenAPI document via an in-process loopback to
   * the SmallRye-served path. See class Javadoc for the rationale.
   */
  private JsonNode fetchCombinedDocument() {
    Client client = ClientBuilder.newClient();
    try {
      String json = client.target(COMBINED_LOOPBACK_URL).request(MediaType.APPLICATION_JSON).get(String.class);
      return JSON.readTree(json);
    } catch (RuntimeException ex) {
      throw new InternalServerErrorException("Failed to fetch combined OpenAPI document: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new InternalServerErrorException("Failed to parse combined OpenAPI document: " + ex.getMessage(), ex);
    } finally {
      client.close();
    }
  }
}
