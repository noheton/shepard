package de.dlr.shepard.filters;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;

@OpenApiFilter(OpenApiFilter.RunStage.BUILD)
public class ApiPathFilter implements OASFilter {

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    fixOpenApiPaths(openAPI);
  }

  /**
   * Remove the quarkus-injected '/shepard/api' path part from the OpenApi paths
   * <p>
   * The deployment goal is to have the /shepard/api as part of the base path,
   * but not as part of the actual endpoint path. So this part is removed.
   *
   */
  private void fixOpenApiPaths(OpenAPI openAPI) {
    Paths newPaths = OASFactory.createPaths();

    Map<String, PathItem> paths = openAPI.getPaths().getPathItems();
    for (var entry : paths.entrySet()) {
      // modify i.e. /shepard/api/collections -> /collections
      String modifiedPath = entry.getKey().replace("/shepard/api", "");
      newPaths.addPathItem(modifiedPath, entry.getValue());
    }
    openAPI.setPaths(newPaths);
  }
}
