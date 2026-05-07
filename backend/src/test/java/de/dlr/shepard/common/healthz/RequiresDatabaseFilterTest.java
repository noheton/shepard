package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.configuration.infrastructure.SpatialDataConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class RequiresDatabaseFilterTest {

  // Resource fixtures used to drive ResourceInfo
  @RequiresDatabase(DatabaseKind.SPATIAL)
  static class SpatialClassResource {

    public void unannotatedMethod() {}

    @RequiresDatabase(DatabaseKind.MONGO)
    public void mongoMethod() {}
  }

  static class UnannotatedResource {

    public void plain() {}
  }

  private static Method method(Class<?> klass, String name) throws NoSuchMethodException {
    return klass.getDeclaredMethod(name);
  }

  private static RequiresDatabaseFilter filter(
    ResourceInfo resourceInfo,
    DbHealthRegistry registry,
    SpatialDataConfig spatialConfig,
    Duration recoveryInterval
  ) {
    RequiresDatabaseFilter f = new RequiresDatabaseFilter();
    f.resourceInfo = resourceInfo;
    f.registry = registry;
    f.spatialDataConfig = spatialConfig;
    f.recoveryInterval = recoveryInterval;
    return f;
  }

  @SuppressWarnings("unchecked")
  private static AtomicReference<Response> capturingContext(ContainerRequestContext ctx) {
    AtomicReference<Response> captured = new AtomicReference<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPath()).thenReturn("data/spatialdata/42");
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    doAnswer(inv -> {
      captured.set(inv.getArgument(0, Response.class));
      return null;
    })
      .when(ctx)
      .abortWith(any(Response.class));
    return captured;
  }

  @Test
  public void noAnnotation_passesThrough() throws Exception {
    ResourceInfo info = mock(ResourceInfo.class);
    when(info.getResourceMethod()).thenReturn(method(UnannotatedResource.class, "plain"));
    when(info.getResourceClass()).thenAnswer(inv -> UnannotatedResource.class);

    DbHealthRegistry registry = mock(DbHealthRegistry.class);
    SpatialDataConfig spatial = mock(SpatialDataConfig.class);

    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    capturingContext(ctx);

    filter(info, registry, spatial, Duration.ofSeconds(15)).filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  @Test
  public void allUp_passesThrough() throws Exception {
    ResourceInfo info = mock(ResourceInfo.class);
    when(info.getResourceMethod()).thenReturn(method(SpatialClassResource.class, "unannotatedMethod"));
    when(info.getResourceClass()).thenAnswer(inv -> SpatialClassResource.class);

    DbHealthRegistry registry = mock(DbHealthRegistry.class);
    when(registry.isCurrentlyDown(DatabaseKind.SPATIAL)).thenReturn(false);
    SpatialDataConfig spatial = mock(SpatialDataConfig.class);
    when(spatial.isEnabled()).thenReturn(true);

    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    AtomicReference<Response> captured = capturingContext(ctx);

    filter(info, registry, spatial, Duration.ofSeconds(15)).filter(ctx);

    assertNull(captured.get());
    verify(ctx, never()).abortWith(any());
  }

  @Test
  public void spatialDownToggleOn_aborts503WithProblemBodyAndRetryAfter() throws Exception {
    ResourceInfo info = mock(ResourceInfo.class);
    when(info.getResourceMethod()).thenReturn(method(SpatialClassResource.class, "unannotatedMethod"));
    when(info.getResourceClass()).thenAnswer(inv -> SpatialClassResource.class);

    DbHealthRegistry registry = mock(DbHealthRegistry.class);
    when(registry.isCurrentlyDown(DatabaseKind.SPATIAL)).thenReturn(true);
    when(registry.lastSuccessfulPingMs(DatabaseKind.SPATIAL)).thenReturn(0L);
    SpatialDataConfig spatial = mock(SpatialDataConfig.class);
    when(spatial.isEnabled()).thenReturn(true);

    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    AtomicReference<Response> captured = capturingContext(ctx);

    filter(info, registry, spatial, Duration.ofSeconds(15)).filter(ctx);

    Response r = captured.get();
    assertNotNull(r);
    assertEquals(503, r.getStatus());
    assertEquals("15", r.getHeaderString(HttpHeaders.RETRY_AFTER));

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertEquals(RequiresDatabaseFilter.PROBLEM_TYPE_DB_UNAVAILABLE, body.get("type"));
    assertEquals("Database unavailable", body.get("title"));
    assertEquals(503, body.get("status"));
    assertTrue(((String) body.get("detail")).contains("Spatial"));
    assertEquals("data/spatialdata/42", body.get("instance"));
    assertEquals("SPATIAL", body.get("downstreamDatabase"));
    assertEquals(0L, body.get("lastSuccessfulPingMs"));
  }

  @Test
  public void spatialDownToggleOff_aborts404() throws Exception {
    ResourceInfo info = mock(ResourceInfo.class);
    when(info.getResourceMethod()).thenReturn(method(SpatialClassResource.class, "unannotatedMethod"));
    when(info.getResourceClass()).thenAnswer(inv -> SpatialClassResource.class);

    DbHealthRegistry registry = mock(DbHealthRegistry.class);
    SpatialDataConfig spatial = mock(SpatialDataConfig.class);
    when(spatial.isEnabled()).thenReturn(false);

    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    AtomicReference<Response> captured = capturingContext(ctx);

    filter(info, registry, spatial, Duration.ofSeconds(15)).filter(ctx);

    Response r = captured.get();
    assertNotNull(r);
    assertEquals(404, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertEquals(404, body.get("status"));
    assertEquals("SPATIAL", body.get("downstreamDatabase"));
    // toggle-off path should not consult registry
    verify(registry, never()).isCurrentlyDown(any());
  }

  @Test
  public void methodAnnotation_overridesClassAnnotation() throws Exception {
    // Class declares SPATIAL, method declares MONGO. Method annotation should win.
    ResourceInfo info = mock(ResourceInfo.class);
    when(info.getResourceMethod()).thenReturn(method(SpatialClassResource.class, "mongoMethod"));
    when(info.getResourceClass()).thenAnswer(inv -> SpatialClassResource.class);

    DbHealthRegistry registry = mock(DbHealthRegistry.class);
    when(registry.isCurrentlyDown(DatabaseKind.MONGO)).thenReturn(true);
    when(registry.lastSuccessfulPingMs(DatabaseKind.MONGO)).thenReturn(123L);
    SpatialDataConfig spatial = mock(SpatialDataConfig.class);
    // toggle would matter only if the SPATIAL class-level annotation were honoured;
    // it should be ignored because the method annotation takes precedence.
    when(spatial.isEnabled()).thenReturn(false);

    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    AtomicReference<Response> captured = capturingContext(ctx);

    filter(info, registry, spatial, Duration.ofSeconds(30)).filter(ctx);

    Response r = captured.get();
    assertNotNull(r);
    assertEquals(503, r.getStatus(), "method-level annotation should drive enforcement");
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertEquals("MONGO", body.get("downstreamDatabase"));
    assertEquals(123L, body.get("lastSuccessfulPingMs"));
    assertEquals("30", r.getHeaderString(HttpHeaders.RETRY_AFTER));
  }
}
