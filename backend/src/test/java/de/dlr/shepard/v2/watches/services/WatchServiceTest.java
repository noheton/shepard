package de.dlr.shepard.v2.watches.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.watches.daos.WatchDAO;
import de.dlr.shepard.v2.watches.entities.Watch;
import de.dlr.shepard.v2.watches.io.WatchIO;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-COLLECTION-WATCHES-IN-MEMORY-PAGING — unit tests for
 * {@link WatchService#count} and bounded {@link WatchService#list(String, int, int)}.
 */
class WatchServiceTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-b000-000000000001";
  static final String ALICE = "alice";

  @Mock
  WatchDAO watchDAO;
  @Mock
  CollectionDAO collectionDAO;
  @Mock
  de.dlr.shepard.context.collection.services.CollectionService collectionService;
  @Mock
  UserService userService;
  @Mock
  TimeseriesContainerService timeseriesContainerService;
  @Mock
  FileContainerService fileContainerService;
  @Mock
  StructuredDataContainerService structuredDataContainerService;

  WatchService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new WatchService();
    service.watchDAO = watchDAO;
    service.collectionDAO = collectionDAO;
    service.collectionService = collectionService;
    service.userService = userService;
    service.timeseriesContainerService = timeseriesContainerService;
    service.fileContainerService = fileContainerService;
    service.structuredDataContainerService = structuredDataContainerService;

    when(userService.getCurrentUser()).thenReturn(new User(ALICE));
    Collection coll = new Collection();
    when(collectionDAO.findByAppId(COLL_APP_ID, ALICE)).thenReturn(coll);
  }

  // ─── count() ─────────────────────────────────────────────────────────────

  @Test
  void countReturnsDaoResult() {
    when(watchDAO.countByCollectionAppId(COLL_APP_ID)).thenReturn(7L);

    long result = service.count(COLL_APP_ID);

    assertEquals(7L, result);
    verify(watchDAO).countByCollectionAppId(COLL_APP_ID);
  }

  @Test
  void countThrowsNotFoundWhenCollectionMissing() {
    when(collectionDAO.findByAppId(COLL_APP_ID, ALICE)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.count(COLL_APP_ID));
    verify(watchDAO, never()).countByCollectionAppId(anyString());
  }

  // ─── list(String, int, int) ───────────────────────────────────────────────

  @Test
  void boundedListCallsDaoWithSkipAndLimit() {
    Watch w = makeWatch("w-app-1");
    when(watchDAO.findByCollectionAppId(COLL_APP_ID, 10, 5)).thenReturn(List.of(w));

    List<WatchIO> result = service.list(COLL_APP_ID, 10, 5);

    assertEquals(1, result.size());
    verify(watchDAO).findByCollectionAppId(COLL_APP_ID, 10, 5);
    verify(watchDAO, never()).findByCollectionAppId(anyString());
  }

  @Test
  void boundedListThrowsNotFoundWhenCollectionMissing() {
    when(collectionDAO.findByAppId(COLL_APP_ID, ALICE)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.list(COLL_APP_ID, 0, 50));
    verify(watchDAO, never()).findByCollectionAppId(anyString(), anyInt(), anyInt());
  }

  @Test
  void boundedListReturnsEmptyWhenDaoReturnsEmpty() {
    when(watchDAO.findByCollectionAppId(COLL_APP_ID, 100, 50)).thenReturn(List.of());

    List<WatchIO> result = service.list(COLL_APP_ID, 100, 50);

    assertEquals(0, result.size());
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private Watch makeWatch(String appId) {
    Watch w = new Watch();
    w.setAppId(appId);
    w.setCollectionAppId(COLL_APP_ID);
    w.setContainerAppId("019e0000-0000-0000-0000-000000000001");
    w.setContainerKind(Watch.Kind.TIMESERIES);
    w.setSince(System.currentTimeMillis());
    w.setAddedBy(ALICE);
    return w;
  }
}
