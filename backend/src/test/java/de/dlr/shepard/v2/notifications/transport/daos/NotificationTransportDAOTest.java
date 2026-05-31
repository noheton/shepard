package de.dlr.shepard.v2.notifications.transport.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — DAO tests with a mocked OGM session.
 *
 * <p>Covers: empty findByAppId returns empty; populated find returns
 * first hit; delete-by-appId returns false when missing, true + invokes
 * session.delete when present; class wires {@link NotificationTransport}
 * as its entity type.
 */
class NotificationTransportDAOTest {

  private Session session;
  private NotificationTransportDAO dao;

  @BeforeEach
  void setUp() throws Exception {
    session = mock(Session.class);
    dao = new NotificationTransportDAO();
    // `session` is a protected field on GenericDAO and lives in a
    // different package than this test; reflect to inject the mock.
    Field f = GenericDAO.class.getDeclaredField("session");
    f.setAccessible(true);
    f.set(dao, session);
  }

  @Test
  void getEntityTypeIsNotificationTransport() {
    assertEquals(NotificationTransport.class, dao.getEntityType());
  }

  @Test
  void findByAppId_returnsEmptyWhenAppIdNull() {
    assertTrue(dao.findByAppId(null).isEmpty());
    verify(session, never()).loadAll(any(Class.class), any(Filter.class), anyInt());
  }

  @Test
  void findByAppId_returnsEmptyWhenAppIdBlank() {
    assertTrue(dao.findByAppId("   ").isEmpty());
    verify(session, never()).loadAll(any(Class.class), any(Filter.class), anyInt());
  }

  @Test
  void findByAppId_returnsFirstHitWhenPresent() {
    NotificationTransport hit = new NotificationTransport();
    hit.setAppId("app-1");
    hit.setName("primary");
    when(session.loadAll(eq(NotificationTransport.class), any(Filter.class), eq(1)))
      .thenReturn(List.of(hit));

    var got = dao.findByAppId("app-1");

    assertTrue(got.isPresent());
    assertEquals("app-1", got.get().getAppId());
    assertEquals("primary", got.get().getName());
  }

  @Test
  void findByAppId_returnsEmptyWhenNoHit() {
    when(session.loadAll(eq(NotificationTransport.class), any(Filter.class), eq(1)))
      .thenReturn(List.of());

    assertTrue(dao.findByAppId("missing").isEmpty());
  }

  @Test
  void deleteByAppId_returnsFalseWhenMissing() {
    when(session.loadAll(eq(NotificationTransport.class), any(Filter.class), eq(1)))
      .thenReturn(List.of());

    assertFalse(dao.deleteByAppId("missing"));
    verify(session, never()).delete(any());
  }

  @Test
  void deleteByAppId_deletesAndReturnsTrueWhenPresent() {
    NotificationTransport hit = new NotificationTransport();
    hit.setAppId("app-1");
    when(session.loadAll(eq(NotificationTransport.class), any(Filter.class), eq(1)))
      .thenReturn(List.of(hit));

    assertTrue(dao.deleteByAppId("app-1"));
    verify(session, times(1)).delete(hit);
  }
}
