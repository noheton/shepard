package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TimeseriesContainerStatsRest} after APISIMP-CONT-NS-COLLAPSE-7 —
 * path migrated from {@code /v2/timeseries-containers/{containerAppId}/stats} to
 * {@code /v2/containers/{containerAppId}/stats}; auth + kind-guard are now explicit.
 */
public class TimeseriesContainerStatsRestTest {

  private TimeseriesContainerStatsRest resource;
  private ContainersV2Service containersV2ServiceMock;
  private PermissionsService permissionsServiceMock;
  private EntityManager entityManagerMock;
  private SecurityContext sc;

  private static final String CONTAINER_APP_ID = "01234567-abcd-7000-8000-000000000001";
  private static final long   CONTAINER_OGM_ID = 99L;
  private static final String CALLER = "test-user";

  @BeforeEach
  void setUp() throws Exception {
    resource = new TimeseriesContainerStatsRest();
    containersV2ServiceMock = mock(ContainersV2Service.class);
    permissionsServiceMock  = mock(PermissionsService.class);
    entityManagerMock       = mock(EntityManager.class);

    inject(resource, "containersV2Service", containersV2ServiceMock);
    inject(resource, "permissionsService",  permissionsServiceMock);
    inject(resource, "entityManager",       entityManagerMock);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(CALLER);
    sc = mock(SecurityContext.class);
    when(sc.getUserPrincipal()).thenReturn(principal);
  }

  // ── auth ─────────────────────────────────────────────────────────────────

  @Test
  void getStats_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getStats(CONTAINER_APP_ID, sc);
    assertEquals(401, r.getStatus());
    verify(containersV2ServiceMock, never()).resolveByAppId(anyString());
  }

  // ── resolution ───────────────────────────────────────────────────────────

  @Test
  void getStats_returns404WhenUnknownAppId() {
    when(containersV2ServiceMock.resolveByAppId(CONTAINER_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.getStats(CONTAINER_APP_ID, sc);
    assertEquals(404, r.getStatus());
    verify(entityManagerMock, never()).createNativeQuery(anyString());
  }

  // ── kind guard ───────────────────────────────────────────────────────────

  @Test
  void getStats_returns415WhenNonTimeseriesContainer() {
    ContainerKindHandler fileHandler = mock(ContainerKindHandler.class);
    when(fileHandler.kind()).thenReturn("file");
    TimeseriesContainer container = containerWithId(CONTAINER_OGM_ID);
    var resolved = Optional.of(new ContainersV2Service.ResolvedContainer(fileHandler, container));
    when(containersV2ServiceMock.resolveByAppId(CONTAINER_APP_ID)).thenReturn(resolved);
    when(permissionsServiceMock.isAccessTypeAllowedForUser(eq(CONTAINER_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);

    Response r = resource.getStats(CONTAINER_APP_ID, sc);

    assertEquals(415, r.getStatus());
    verify(entityManagerMock, never()).createNativeQuery(anyString());
  }

  // ── happy path ───────────────────────────────────────────────────────────

  @Test
  void getStats_knownTsAppId_resolvesToOgmIdForSql() {
    ContainerKindHandler tsHandler = mock(ContainerKindHandler.class);
    when(tsHandler.kind()).thenReturn("timeseries");
    TimeseriesContainer container = containerWithId(CONTAINER_OGM_ID);
    var resolved = Optional.of(new ContainersV2Service.ResolvedContainer(tsHandler, container));
    when(containersV2ServiceMock.resolveByAppId(CONTAINER_APP_ID)).thenReturn(resolved);
    when(permissionsServiceMock.isAccessTypeAllowedForUser(eq(CONTAINER_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);

    Query pointsQuery = mock(Query.class);
    Query recentQuery = mock(Query.class);
    when(entityManagerMock.createNativeQuery(org.mockito.ArgumentMatchers.contains("point_count")))
      .thenReturn(pointsQuery);
    when(entityManagerMock.createNativeQuery(org.mockito.ArgumentMatchers.contains("windowStart")))
      .thenReturn(recentQuery);
    when(pointsQuery.setParameter(anyString(), any())).thenReturn(pointsQuery);
    when(recentQuery.setParameter(anyString(), any())).thenReturn(recentQuery);
    when(pointsQuery.getSingleResult()).thenReturn(new Object[]{100L, 5L});
    when(recentQuery.getSingleResult()).thenReturn(10L);

    Response r = resource.getStats(CONTAINER_APP_ID, sc);

    assertEquals(200, r.getStatus());
    verify(containersV2ServiceMock).resolveByAppId(CONTAINER_APP_ID);
    verify(pointsQuery).setParameter("cid", CONTAINER_OGM_ID);
  }

  @Test
  void getStats_returns403WhenPermissionDenied() {
    ContainerKindHandler tsHandler = mock(ContainerKindHandler.class);
    when(tsHandler.kind()).thenReturn("timeseries");
    TimeseriesContainer container = containerWithId(CONTAINER_OGM_ID);
    var resolved = Optional.of(new ContainersV2Service.ResolvedContainer(tsHandler, container));
    when(containersV2ServiceMock.resolveByAppId(CONTAINER_APP_ID)).thenReturn(resolved);
    when(permissionsServiceMock.isAccessTypeAllowedForUser(eq(CONTAINER_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);

    Response r = resource.getStats(CONTAINER_APP_ID, sc);

    assertEquals(403, r.getStatus());
    verify(entityManagerMock, never()).createNativeQuery(anyString());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static TimeseriesContainer containerWithId(long id) {
    TimeseriesContainer c = new TimeseriesContainer();
    c.setId(id);
    return c;
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException("No field '" + fieldName + "' on " + target.getClass());
  }
}
