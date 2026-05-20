package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionContainersDAO;
import de.dlr.shepard.v2.collection.io.ContainerSummaryIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionContainersRestTest {

  static final String COLL_APP_ID = "coll-appid-1";
  static final long   COLL_OGM_ID = 42L;
  static final String CALLER      = "alice";

  @Mock CollectionContainersDAO containersDAO;
  @Mock PermissionsService permissionsService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  CollectionContainersRest resource;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionContainersRest();
    resource.containersDAO = containersDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(containersDAO.findByCollectionAppId(COLL_APP_ID))
      .thenReturn(List.of(
        new ContainerSummaryIO(1L, "ts-app-1", "HotfireTS", "TIMESERIES"),
        new ContainerSummaryIO(2L, "fb-app-1", "HotfireFiles", "FILE")
      ));
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(COLL_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    assertThat(resource.list(COLL_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    assertThat(resource.list(COLL_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void list_returns200WithContainers() {
    var r = resource.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<ContainerSummaryIO>) r.getEntity();
    assertThat(body).hasSize(2);
    assertThat(body.get(0).getContainerType()).isEqualTo("TIMESERIES");
    assertThat(body.get(1).getContainerType()).isEqualTo("FILE");
  }

  @Test
  void list_returns200WithEmptyListWhenNoContainers() {
    when(containersDAO.findByCollectionAppId(COLL_APP_ID)).thenReturn(List.of());
    var r = resource.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<ContainerSummaryIO>) r.getEntity();
    assertThat(body).isEmpty();
  }

  @Test
  void list_usesCollectionOgmIdForPermissionCheck() {
    resource.list(COLL_APP_ID, sc);
    verify(permissionsService).isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER));
  }
}
