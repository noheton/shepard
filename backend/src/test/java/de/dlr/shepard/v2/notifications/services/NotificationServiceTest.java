package de.dlr.shepard.v2.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.notifications.daos.NotificationDAO;
import de.dlr.shepard.v2.notifications.entities.Notification;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class NotificationServiceTest {

  @Mock
  NotificationDAO dao;

  NotificationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new NotificationService();
    service.dao = dao;
    when(dao.createOrUpdate(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void publishSavesNotificationWithCorrectFields() {
    service.publish(
      NotificationService.AUDIENCE_USER,
      "alice",
      NotificationService.CATEGORY_INFO,
      "system",
      "Hello",
      "Body text",
      "/collections"
    );

    ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
    verify(dao).createOrUpdate(cap.capture());
    Notification saved = cap.getValue();

    assertEquals("USER", saved.getAudience());
    assertEquals("alice", saved.getTargetUsername());
    assertEquals("INFO", saved.getCategory());
    assertEquals("system", saved.getSource());
    assertEquals("Hello", saved.getTitle());
    assertEquals("Body text", saved.getBody());
    assertEquals("/collections", saved.getActionUrl());
    assertFalse(saved.isRead());
    assertNotNull(saved.getCreatedAtMillis());
  }

  @Test
  void publishAllAudienceHasNullTargetUsername() {
    service.publish(
      NotificationService.AUDIENCE_ALL,
      null,
      NotificationService.CATEGORY_WARNING,
      "system",
      "Broadcast",
      "Everyone sees this",
      null
    );

    ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
    verify(dao).createOrUpdate(cap.capture());
    Notification saved = cap.getValue();

    assertEquals("ALL", saved.getAudience());
    assertFalse(saved.isRead());
  }

  @Test
  void listForUserDelegatesToDAO() {
    Notification n = makeNotification("app-1", "alice");
    when(dao.listForUser("alice", false)).thenReturn(List.of(n));

    List<Notification> result = service.listForUser("alice", false);

    assertEquals(1, result.size());
    assertEquals("app-1", result.get(0).getAppId());
  }

  @Test
  void countUnreadDelegatesToDAO() {
    when(dao.countUnread("alice", true)).thenReturn(3L);

    long count = service.countUnread("alice", true);

    assertEquals(3L, count);
  }

  @Test
  void markReadFlipsReadFlag() {
    Notification n = makeNotification("app-1", "alice");
    assertFalse(n.isRead());
    when(dao.findByAppIdForUser("app-1", "alice", false)).thenReturn(Optional.of(n));

    Notification updated = service.markRead("app-1", "alice", false);

    assertTrue(updated.isRead());
    verify(dao).createOrUpdate(n);
  }

  @Test
  void markReadThrowsWhenNotFound() {
    when(dao.findByAppIdForUser(anyString(), anyString(), anyBoolean())).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.markRead("missing", "alice", false));
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void dismissDeletesNode() {
    Notification n = makeNotification("app-2", "bob");
    n.setId(99L);
    when(dao.findByAppIdForUser("app-2", "bob", false)).thenReturn(Optional.of(n));

    service.dismiss("app-2", "bob", false);

    verify(dao).deleteByNeo4jId(99L);
  }

  @Test
  void dismissThrowsWhenNotFound() {
    when(dao.findByAppIdForUser(anyString(), anyString(), anyBoolean())).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.dismiss("missing", "bob", false));
    verify(dao, never()).deleteByNeo4jId(anyLong());
  }

  private Notification makeNotification(String appId, String username) {
    Notification n = new Notification(
      NotificationService.AUDIENCE_USER,
      username,
      NotificationService.CATEGORY_INFO,
      "test",
      "Test title",
      "Test body",
      null
    );
    n.setAppId(appId);
    return n;
  }
}
