package de.dlr.shepard.spi.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;

/**
 * Emits RFC 8594 {@code Sunset} + {@code Deprecation: true} response
 * headers for any resource method (or resource class) annotated with
 * {@link Sunset}.
 *
 * <p>No method/class uses {@link Sunset} in this slice — the filter is
 * a no-op until L2e wires it on. See {@link Sunset} for context.
 */
@Provider
public class SunsetFilter implements ContainerResponseFilter {

  @Context
  ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    Sunset annotation = resolveAnnotation();
    if (annotation == null) return;

    responseContext.getHeaders().putSingle("Sunset", annotation.date());
    responseContext.getHeaders().putSingle("Deprecation", "true");
    if (!annotation.link().isEmpty()) {
      responseContext.getHeaders().add("Link", "<" + annotation.link() + ">; rel=\"sunset\"");
    }
  }

  private Sunset resolveAnnotation() {
    Method method = resourceInfo.getResourceMethod();
    if (method != null) {
      Sunset onMethod = method.getAnnotation(Sunset.class);
      if (onMethod != null) return onMethod;
    }
    Class<?> declaring = resourceInfo.getResourceClass();
    if (declaring != null) {
      return declaring.getAnnotation(Sunset.class);
    }
    return null;
  }
}
