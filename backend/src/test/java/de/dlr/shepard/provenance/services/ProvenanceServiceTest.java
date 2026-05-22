package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProvenanceServiceTest {

  @Mock
  ActivityDAO activityDAO;

  @Mock
  HmacChainService hmacChainService;

  ProvenanceService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ProvenanceService();
    service.activityDAO = activityDAO;
    // PR-3 wiring — supply a no-op chain mock so existing assertions
    // about field values stay intact (the mock's default void return
    // is exactly the no-op we want here).
    service.hmacChainService = hmacChainService;
    service.enabled = true;
    service.originInstance = "test-instance";
    when(activityDAO.createOrUpdate(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void recordPersistsAllSuppliedFields() {
    Activity saved = service.record(
      "CREATE",
      "Collection",
      "appId-1",
      "alice",
      "POST /v2/collections",
      "POST",
      "v2/collections",
      201,
      1_000L,
      2_000L
    );
    assertNotNull(saved);
    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(activityDAO).createOrUpdate(captor.capture());
    Activity a = captor.getValue();
    assertEquals("CREATE", a.getActionKind());
    assertEquals("Collection", a.getTargetKind());
    assertEquals("appId-1", a.getTargetAppId());
    assertEquals("alice", a.getAgentUsername());
    assertEquals("POST /v2/collections", a.getSummary());
    assertEquals("POST", a.getMethod());
    assertEquals("v2/collections", a.getPath());
    assertEquals(Integer.valueOf(201), a.getStatus());
    assertEquals(Long.valueOf(1_000L), a.getStartedAtMillis());
    assertEquals(Long.valueOf(2_000L), a.getEndedAtMillis());
    assertEquals("test-instance", a.getOriginInstance());
  }

  @Test
  void summaryIsTruncatedAt256Chars() {
    String huge = "x".repeat(500);
    service.record("CREATE", null, null, "alice", huge, "POST", "p", 201, 1L, 2L);
    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(activityDAO).createOrUpdate(captor.capture());
    assertEquals(256, captor.getValue().getSummary().length());
  }

  @Test
  void pathIsTruncatedAt1024Chars() {
    String huge = "/" + "p".repeat(2000);
    service.record("CREATE", null, null, "alice", "s", "POST", huge, 201, 1L, 2L);
    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(activityDAO).createOrUpdate(captor.capture());
    assertEquals(1024, captor.getValue().getPath().length());
  }

  @Test
  void disabledServiceDoesNotPersist() {
    service.enabled = false;
    Activity saved = service.record("CREATE", null, null, "alice", "s", "POST", "p", 201, 1L, 2L);
    assertNull(saved);
    verify(activityDAO, never()).createOrUpdate(any());
  }

  @Test
  void daoFailureSwallowed() {
    when(activityDAO.createOrUpdate(any(Activity.class))).thenThrow(new RuntimeException("Neo4j burped"));
    Activity saved = service.record("CREATE", null, null, "alice", "s", "POST", "p", 201, 1L, 2L);
    assertNull(saved);
  }

  @Test
  void listDelegatesToDao() {
    when(activityDAO.list("alice", null, null, null, null, 100)).thenReturn(List.of(new Activity()));
    List<Activity> result = service.list("alice", null, null, null, null, 100);
    assertEquals(1, result.size());
  }

  @Test
  void listEmptyWhenDisabled() {
    service.enabled = false;
    List<Activity> result = service.list("alice", null, null, null, null, 100);
    assertTrue(result.isEmpty());
    verify(activityDAO, never()).list(any(), any(), any(), any(), any(), any(Integer.class));
  }

  @Test
  void isEnabledReflectsConfig() {
    assertTrue(service.isEnabled());
    service.enabled = false;
    assertEquals(false, service.isEnabled());
  }
}
