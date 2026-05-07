package de.dlr.shepard.common.healthz;

import de.dlr.shepard.common.configuration.infrastructure.SpatialDataConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.USER)
public class RequiresDatabaseFilter implements ContainerRequestFilter {

  static final String PROBLEM_TYPE_DB_UNAVAILABLE = "https://shepard.example/probs/db-unavailable";

  @Context
  ResourceInfo resourceInfo;

  @Inject
  DbHealthRegistry registry;

  @Inject
  SpatialDataConfig spatialDataConfig;

  @Inject
  @ConfigProperty(name = "shepard.health.recovery.interval", defaultValue = "PT15S")
  Duration recoveryInterval;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    RequiresDatabase ann = readAnnotation();
    if (ann == null || ann.value() == null || ann.value().length == 0) {
      return;
    }

    for (DatabaseKind kind : ann.value()) {
      // Spatial is conceptually absent when its toggle is off; surface as 404, not 503.
      if (kind == DatabaseKind.SPATIAL && !spatialDataConfig.isEnabled()) {
        abortNotFound(requestContext, kind);
        return;
      }
      // Neo4j is required for almost every endpoint, so a Neo4j blip blanket-503s the
      // API; that is the correct trade-off until/unless individual endpoints are proven
      // Neo4j-independent.
      if (registry.isCurrentlyDown(kind)) {
        abortServiceUnavailable(requestContext, kind);
        return;
      }
    }
  }

  private RequiresDatabase readAnnotation() {
    Method m = resourceInfo.getResourceMethod();
    if (m != null) {
      RequiresDatabase methodAnn = m.getAnnotation(RequiresDatabase.class);
      if (methodAnn != null) {
        return methodAnn;
      }
    }
    Class<?> klass = resourceInfo.getResourceClass();
    if (klass != null) {
      return klass.getAnnotation(RequiresDatabase.class);
    }
    return null;
  }

  private void abortNotFound(ContainerRequestContext ctx, DatabaseKind kind) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "https://shepard.example/probs/feature-disabled");
    body.put("title", "Not Found");
    body.put("status", Status.NOT_FOUND.getStatusCode());
    body.put("detail", humanName(kind) + " feature is disabled by configuration.");
    body.put("instance", ctx.getUriInfo().getPath());
    body.put("downstreamDatabase", kind.name());
    ctx.abortWith(Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).entity(body).build());
  }

  private void abortServiceUnavailable(ContainerRequestContext ctx, DatabaseKind kind) {
    long lastSuccess = registry.lastSuccessfulPingMs(kind);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", PROBLEM_TYPE_DB_UNAVAILABLE);
    body.put("title", "Database unavailable");
    body.put("status", Status.SERVICE_UNAVAILABLE.getStatusCode());
    body.put("detail", humanName(kind) + " database is currently unreachable.");
    body.put("instance", ctx.getUriInfo().getPath());
    body.put("downstreamDatabase", kind.name());
    body.put("lastSuccessfulPingMs", lastSuccess);

    long retryAfterSeconds = Math.max(1L, recoveryInterval.toSeconds());
    Log.warnf(
      "RequiresDatabaseFilter: short-circuiting %s with 503; %s is DOWN (lastSuccessfulPingMs=%d)",
      ctx.getUriInfo().getPath(),
      kind.name(),
      lastSuccess
    );
    ctx.abortWith(
      Response.status(Status.SERVICE_UNAVAILABLE)
        .type(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
        .entity(body)
        .build()
    );
  }

  private static String humanName(DatabaseKind kind) {
    return switch (kind) {
      case NEO4J -> "Neo4j";
      case MONGO -> "MongoDB";
      case TIMESCALE -> "TimescaleDB";
      case SPATIAL -> "Spatial";
    };
  }
}
