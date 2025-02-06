package de.dlr.shepard.common.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.List;

public class PublicEndpointRegistry {

  // TODO: Remove /spatialDataContainer
  private static final List<String> publicPaths = List.of("/versionz", "/spatialDataContainer");

  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    return publicPaths
      .stream()
      .anyMatch(path -> {
        return requestContext.getUriInfo().getPath().startsWith(path);
      });
  }
}
