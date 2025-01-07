package de.dlr.shepard.common.filters;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Schema;

@OpenApiFilter(OpenApiFilter.RunStage.BUILD)
public class ApiPathFilter implements OASFilter {

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    fixOpenApiPaths(openAPI);
    excludeExtraHealthEndpointsAndAdjustHealthzTag(openAPI);
    sortPropertiesOfHealthCheckSchemas(openAPI);
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

  /**
   * Clean the health check endpoints in OpenAPI documentation.
   */
  private void excludeExtraHealthEndpointsAndAdjustHealthzTag(OpenAPI openAPI) {
    Paths newPaths = OASFactory.createPaths();

    Map<String, PathItem> paths = openAPI.getPaths().getPathItems();

    newPaths.setPathItems(
      paths
        .entrySet()
        .stream()
        .filter(path -> !List.of("/healthz/ready", "/healthz/live", "/healthz/started").contains(path.getKey()))
        .map(path -> {
          if (
            path.getValue().getGET() == null || !path.getValue().getGET().getTags().contains("MicroProfile Health")
          ) return path;

          PathItem newPathItem = path.getValue();
          newPathItem.getGET().setTags(List.of("healthz"));
          newPathItem.getGET().setOperationId("getServerHealth");
          return Map.entry(path.getKey(), newPathItem);
        })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
    );
    openAPI.setPaths(newPaths);
  }

  private void sortPropertiesOfHealthCheckSchemas(OpenAPI openAPI) {
    Map<String, Schema> schemas = openAPI.getComponents().getSchemas();

    openAPI
      .getComponents()
      .setSchemas(
        schemas
          .entrySet()
          .stream()
          .map(schema -> {
            if (!List.of("HealthCheck", "HealthResponse").contains(schema.getKey())) {
              return schema;
            }

            schema
              .getValue()
              .setProperties(
                schema
                  .getValue()
                  .getProperties()
                  .entrySet()
                  .stream()
                  .sorted(Map.Entry.comparingByKey())
                  .collect(
                    Collectors.toMap(
                      Map.Entry::getKey,
                      Map.Entry::getValue,
                      (oldValue, newValue) -> oldValue,
                      LinkedHashMap::new
                    )
                  )
              );
            return schema;
          })
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
      );
  }
}
