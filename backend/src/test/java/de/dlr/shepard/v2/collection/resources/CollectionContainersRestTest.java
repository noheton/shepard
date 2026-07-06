package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionContainersDAO;
import de.dlr.shepard.v2.collection.io.ContainerSummaryIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
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
  static final int    PAGE        = 0;
  static final int    PAGE_SIZE   = 50;

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
    when(containersDAO.countByCollectionAppId(COLL_APP_ID)).thenReturn(2L);
    when(containersDAO.findByCollectionAppId(eq(COLL_APP_ID), anyLong(), anyInt()))
      .thenReturn(List.of(
        new ContainerSummaryIO("ts-app-1", "HotfireTS", "TIMESERIES"),
        new ContainerSummaryIO("fb-app-1", "HotfireFiles", "FILE")
      ));
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    assertThat(resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    assertThat(resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc).getStatus()).isEqualTo(403);
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_returns200WithPagedEnvelope() {
    var r = resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var body = (PagedResponseIO<ContainerSummaryIO>) r.getEntity();
    assertThat(body.items()).hasSize(2);
    assertThat(body.total()).isEqualTo(2L);
    assertThat(body.page()).isEqualTo(PAGE);
    assertThat(body.pageSize()).isEqualTo(PAGE_SIZE);
    assertThat(body.items().get(0).getContainerType()).isEqualTo("TIMESERIES");
    assertThat(body.items().get(1).getContainerType()).isEqualTo("FILE");
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_xTotalCountHeaderEqualsTotalField() {
    var r = resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc);
    var body = (PagedResponseIO<ContainerSummaryIO>) r.getEntity();
    assertThat(r.getHeaderString("X-Total-Count")).isEqualTo(String.valueOf(body.total()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_returns200WithEmptyItemsWhenNoContainers() {
    when(containersDAO.countByCollectionAppId(COLL_APP_ID)).thenReturn(0L);
    when(containersDAO.findByCollectionAppId(eq(COLL_APP_ID), anyLong(), anyInt())).thenReturn(List.of());
    var r = resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var body = (PagedResponseIO<ContainerSummaryIO>) r.getEntity();
    assertThat(body.items()).isEmpty();
    assertThat(body.total()).isEqualTo(0L);
  }

  @Test
  void list_passesCorrectSkipToDao() {
    int p = 2;
    int ps = 10;
    resource.list(COLL_APP_ID, p, ps, sc);
    verify(containersDAO).findByCollectionAppId(eq(COLL_APP_ID), eq((long) p * ps), eq(ps));
  }

  @Test
  void list_usesCollectionOgmIdForPermissionCheck() {
    resource.list(COLL_APP_ID, PAGE, PAGE_SIZE, sc);
    verify(permissionsService).isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER));
  }
}
