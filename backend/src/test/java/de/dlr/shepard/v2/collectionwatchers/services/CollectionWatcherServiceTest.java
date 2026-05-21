package de.dlr.shepard.v2.collectionwatchers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.collectionwatchers.daos.CollectionWatcherDAO;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import de.dlr.shepard.v2.collectionwatchers.io.CollectionWatcherIO;
import de.dlr.shepard.v2.collectionwatchers.services.CollectionWatcherService;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionWatcherServiceTest {

  static final String COLLECTION_APP_ID = "019e3c96-0000-7000-a000-000000000001";
  static final long COLLECTION_OGM_ID = 42L;
  static final String ALICE = "alice";
  static final String BOB = "bob";

  @Mock
  CollectionWatcherDAO dao;

  @Mock
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  UserService userService;

  @Mock
  NotificationService notificationService;

  CollectionWatcherService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new CollectionWatcherService();
    service.dao = dao;
    service.collectionPropertiesDAO = collectionPropertiesDAO;
    service.permissionsService = permissionsService;
    service.userService = userService;
    service.notificationService = notificationService;

    // Default: collection exists and caller has Read.
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLLECTION_APP_ID))
      .thenReturn(Optional.of(COLLECTION_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Read), anyString(), anyLong()))
      .thenReturn(true);
    // Default: dao saves return the passed entity unchanged.
    when(dao.createOrUpdate(any(CollectionWatcher.class)))
      .thenAnswer(inv -> {
        CollectionWatcher w = inv.getArgument(0);
        if (w.getAppId() == null) w.setAppId("generated-app-id");
        return w;
      });
  }

  // ─── watch() ─────────────────────────────────────────────────────────────

  @Test
  void watchCreatesNewRecordWhenNotAlreadyWatching() {
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(null);

    CollectionWatcherIO result = service.watch(COLLECTION_APP_ID, ALICE);

    assertNotNull(result);
    assertEquals(ALICE, result.username());
    assertEquals(COLLECTION_APP_ID, result.collectionAppId());
    assertNotNull(result.since());

    ArgumentCaptor<CollectionWatcher> cap = ArgumentCaptor.forClass(CollectionWatcher.class);
    verify(dao).createOrUpdate(cap.capture());
    assertEquals(ALICE, cap.getValue().getUsername());
    assertEquals(COLLECTION_APP_ID, cap.getValue().getCollectionAppId());
  }

  @Test
  void watchIsIdempotentWhenAlreadyWatching() {
    CollectionWatcher existing = makeWatcher(ALICE, COLLECTION_APP_ID, "existing-app-id");
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(existing);

    CollectionWatcherIO result = service.watch(COLLECTION_APP_ID, ALICE);

    assertEquals("existing-app-id", result.watcherAppId());
    // createOrUpdate should NOT be called — existing record returned directly.
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void watchThrowsForbiddenWhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()))
      .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> service.watch(COLLECTION_APP_ID, ALICE));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void watchThrowsNotFoundWhenCollectionMissing() {
    when(collectionPropertiesDAO.findCollectionIdByAppId("nonexistent")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.watch("nonexistent", ALICE));
  }

  // ─── unwatch() ───────────────────────────────────────────────────────────

  @Test
  void unwatchDeletesExistingRecord() {
    CollectionWatcher existing = makeWatcher(ALICE, COLLECTION_APP_ID, "app-1");
    existing.setId(99L);
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(existing);

    service.unwatch(COLLECTION_APP_ID, ALICE);

    verify(dao).deleteByNeo4jId(99L);
  }

  @Test
  void unwatchIsIdempotentWhenNotWatching() {
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(null);

    // Should not throw.
    service.unwatch(COLLECTION_APP_ID, ALICE);

    verify(dao, never()).deleteByNeo4jId(anyLong());
  }

  // ─── getMe() ─────────────────────────────────────────────────────────────

  @Test
  void getMeReturnsPresentWhenWatching() {
    CollectionWatcher w = makeWatcher(ALICE, COLLECTION_APP_ID, "app-2");
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(w);

    Optional<CollectionWatcherIO> result = service.getMe(COLLECTION_APP_ID, ALICE);

    assertTrue(result.isPresent());
    assertEquals("app-2", result.get().watcherAppId());
  }

  @Test
  void getMeReturnsEmptyWhenNotWatching() {
    when(dao.findByUsernameAndCollection(ALICE, COLLECTION_APP_ID)).thenReturn(null);

    Optional<CollectionWatcherIO> result = service.getMe(COLLECTION_APP_ID, ALICE);

    assertFalse(result.isPresent());
  }

  // ─── list() ──────────────────────────────────────────────────────────────

  @Test
  void listReturnsMappedWatchers() {
    List<CollectionWatcher> watchers = List.of(
      makeWatcher(ALICE, COLLECTION_APP_ID, "app-a"),
      makeWatcher(BOB, COLLECTION_APP_ID, "app-b")
    );
    when(dao.findByCollectionAppId(COLLECTION_APP_ID)).thenReturn(watchers);

    List<CollectionWatcherIO> result = service.list(COLLECTION_APP_ID, ALICE);

    assertEquals(2, result.size());
    assertEquals("app-a", result.get(0).watcherAppId());
    assertEquals("app-b", result.get(1).watcherAppId());
  }

  @Test
  void listThrowsForbiddenWhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLLECTION_OGM_ID), eq(AccessType.Read), eq(ALICE), anyLong()))
      .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> service.list(COLLECTION_APP_ID, ALICE));
    verify(dao, never()).findByCollectionAppId(anyString());
  }

  // ─── notifyWatchersOfNewDataObject() ─────────────────────────────────────

  @Test
  void notifyWatchersSendsNotificationToEachWatcher() {
    when(dao.findWatcherUsernamesByCollectionAppId(COLLECTION_APP_ID))
      .thenReturn(List.of(ALICE, BOB));

    service.notifyWatchersOfNewDataObject(COLLECTION_APP_ID, "My Collection", "Dataset-1", 42L);

    verify(notificationService).publish(
      eq(NotificationService.AUDIENCE_USER),
      eq(ALICE),
      eq(NotificationService.CATEGORY_INFO),
      eq("collection-watch"),
      anyString(),
      anyString(),
      anyString()
    );
    verify(notificationService).publish(
      eq(NotificationService.AUDIENCE_USER),
      eq(BOB),
      eq(NotificationService.CATEGORY_INFO),
      eq("collection-watch"),
      anyString(),
      anyString(),
      anyString()
    );
  }

  @Test
  void notifyWatchersDoesNothingWhenNoWatchers() {
    when(dao.findWatcherUsernamesByCollectionAppId(COLLECTION_APP_ID)).thenReturn(List.of());

    service.notifyWatchersOfNewDataObject(COLLECTION_APP_ID, "My Collection", "Dataset-1", 42L);

    verify(notificationService, never()).publish(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void notifyWatchersIsBestEffortOnException() {
    when(dao.findWatcherUsernamesByCollectionAppId(COLLECTION_APP_ID))
      .thenThrow(new RuntimeException("DB down"));

    // Should NOT throw — swallows the exception.
    service.notifyWatchersOfNewDataObject(COLLECTION_APP_ID, "My Collection", "Dataset-1", 42L);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private CollectionWatcher makeWatcher(String username, String collectionAppId, String appId) {
    CollectionWatcher w = new CollectionWatcher();
    w.setUsername(username);
    w.setCollectionAppId(collectionAppId);
    w.setAppId(appId);
    w.setSince(System.currentTimeMillis());
    return w;
  }
}
