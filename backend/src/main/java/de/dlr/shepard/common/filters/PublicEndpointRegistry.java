package de.dlr.shepard.common.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.List;

public class PublicEndpointRegistry {

  private static final List<String> publicPaths = List.of("/versionz");

  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    return publicPaths
      .stream()
      .anyMatch(path -> {
        return requestContext.getUriInfo().getPath().startsWith(path);
      });
  }
}
