package de.dlr.shepard.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.List;

public class PublicEndpointRegistry {

  private static final List<String> publicPaths = List.of("/versionz");

  public static boolean isRequestPathPublic(ContainerRequestContext requestContext) {
    return publicPaths.contains(requestContext.getUriInfo().getPath());
  }
}
