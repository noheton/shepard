package de.dlr.shepard.data.hdf.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.events.PermissionsChangedEvent;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HdfPermissionBridgeTest {

  private HsdsClient hsdsClient;
  private HdfContainerDAO dao;
  private PermissionsService permissionsService;
  @SuppressWarnings("unchecked")
  private Instance<HsdsClient> hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);

  private HdfPermissionBridge bridge;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    hsdsClient = mock(HsdsClient.class);
    dao = mock(HdfContainerDAO.class);
    permissionsService = mock(PermissionsService.class);
    hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);
    when(hsdsInstance.isUnsatisfied()).thenReturn(false);
    when(hsdsInstance.get()).thenReturn(hsdsClient);
    bridge = new HdfPermissionBridge(hsdsInstance, dao, permissionsService);
  }

  private HdfContainer makeContainer(long id, String domain) {
    HdfContainer c = new HdfContainer(id);
    c.setAppId("appid-" + id);
    c.setHsdsDomain(domain);
    return c;
  }

  private Permissions makePerms(String owner, List<String> readers, List<String> writers, List<String> managers) {
    User ownerUser = owner == null ? null : new User(owner);
    Permissions p = new Permissions(
      ownerUser,
      readers.stream().map(User::new).toList(),
      writers.stream().map(User::new).toList(),
      List.of(),
      List.of(),
      managers.stream().map(User::new).toList(),
      PermissionType.Private
    );
    return p;
  }

  @Test
  void nonHdfContainerEventIsIgnored() {
    var ev = new PermissionsChangedEvent(42L, "Collection", "ap42", PermissionsChangedEvent.Kind.UPDATED);
    bridge.onPermissionsChanged(ev);
    verify(dao, never()).findByNeo4jId(anyLong());
    verify(hsdsClient, never()).setDomainAcl(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void nullEventIgnored() {
    bridge.onPermissionsChanged(null);
    verify(dao, never()).findByNeo4jId(anyLong());
  }

  @Test
  void hdfContainerEventSyncsAcl() {
    var container = makeContainer(7L, "/shepard/x/");
    when(dao.findByNeo4jId(7L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(7L)).thenReturn(
      Optional.of(makePerms("alice", List.of("bob"), List.of("carol"), List.of("dave")))
    );

    var ev = new PermissionsChangedEvent(7L, "HdfContainer", "appid-7", PermissionsChangedEvent.Kind.UPDATED);
    bridge.onPermissionsChanged(ev);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> readersCap = ArgumentCaptor.forClass(Collection.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> writersCap = ArgumentCaptor.forClass(Collection.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> managersCap = ArgumentCaptor.forClass(Collection.class);
    verify(hsdsClient).setDomainAcl(eq("/shepard/x/"), eq("alice"), readersCap.capture(), writersCap.capture(), managersCap.capture());
    assertTrue(readersCap.getValue().contains("bob"));
    assertTrue(writersCap.getValue().contains("carol"));
    assertTrue(managersCap.getValue().contains("dave"));
  }

  @Test
  void missingContainerSkipsSilently() {
    when(dao.findByNeo4jId(99L)).thenReturn(null);
    var ev = new PermissionsChangedEvent(99L, "HdfContainer", null, PermissionsChangedEvent.Kind.DELETED);
    bridge.onPermissionsChanged(ev);
    verify(hsdsClient, never()).setDomainAcl(anyString(), anyString(), any(), any(), any());
    verify(hsdsClient, never()).clearDomainAcl(anyString());
  }

  @Test
  void containerWithoutDomainIsSkipped() {
    var container = makeContainer(8L, null);
    when(dao.findByNeo4jId(8L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(8L)).thenReturn(
      Optional.of(makePerms("alice", List.of(), List.of(), List.of()))
    );
    var ev = new PermissionsChangedEvent(8L, "HdfContainer", "appid-8", PermissionsChangedEvent.Kind.UPDATED);
    bridge.onPermissionsChanged(ev);
    verify(hsdsClient, never()).setDomainAcl(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void missingPermissionsTriggersClearDomainAcl() {
    var container = makeContainer(10L, "/shepard/y/");
    when(dao.findByNeo4jId(10L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(10L)).thenReturn(Optional.empty());

    var ev = new PermissionsChangedEvent(10L, "HdfContainer", "appid-10", PermissionsChangedEvent.Kind.DELETED);
    bridge.onPermissionsChanged(ev);

    verify(hsdsClient).clearDomainAcl("/shepard/y/");
    verify(hsdsClient, never()).setDomainAcl(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void hsdsFailureQueuesRetryDoesNotPropagate() {
    var container = makeContainer(11L, "/shepard/z/");
    when(dao.findByNeo4jId(11L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(11L)).thenReturn(
      Optional.of(makePerms("alice", List.of(), List.of(), List.of()))
    );
    doThrow(new HsdsClient.HsdsException("boom")).when(hsdsClient).setDomainAcl(anyString(), anyString(), any(), any(), any());

    var ev = new PermissionsChangedEvent(11L, "HdfContainer", "appid-11", PermissionsChangedEvent.Kind.UPDATED);
    // does not throw
    bridge.onPermissionsChanged(ev);

    assertEquals(1, bridge.retrySnapshot().size(), "failed sync should be queued");
  }

  @Test
  void daoFailureQueuesRetry() {
    when(dao.findByNeo4jId(12L)).thenThrow(new RuntimeException("neo4j down"));
    var ev = new PermissionsChangedEvent(12L, "HdfContainer", "appid-12", PermissionsChangedEvent.Kind.UPDATED);
    bridge.onPermissionsChanged(ev);
    assertEquals(1, bridge.retrySnapshot().size());
  }

  @Test
  void retryQueueIsCapped() {
    bridge.setRetryCapacity(2);
    var container = makeContainer(20L, "/shepard/w/");
    when(dao.findByNeo4jId(20L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(20L)).thenReturn(
      Optional.of(makePerms("alice", List.of(), List.of(), List.of()))
    );
    doThrow(new HsdsClient.HsdsException("boom")).when(hsdsClient).setDomainAcl(anyString(), anyString(), any(), any(), any());

    bridge.onPermissionsChanged(new PermissionsChangedEvent(20L, "HdfContainer", "a", PermissionsChangedEvent.Kind.UPDATED));
    bridge.onPermissionsChanged(new PermissionsChangedEvent(20L, "HdfContainer", "b", PermissionsChangedEvent.Kind.UPDATED));
    bridge.onPermissionsChanged(new PermissionsChangedEvent(20L, "HdfContainer", "c", PermissionsChangedEvent.Kind.UPDATED));

    assertEquals(2, bridge.retrySnapshot().size(), "queue should drop oldest on overflow");
  }

  @Test
  void drainRetriesPullsAndReattempts() {
    var container = makeContainer(30L, "/shepard/q/");
    when(dao.findByNeo4jId(30L)).thenReturn(container);
    when(permissionsService.getPermissionsOfEntityOptional(30L)).thenReturn(
      Optional.of(makePerms("alice", List.of(), List.of(), List.of()))
    );

    // First call fails -> queued.
    doThrow(new HsdsClient.HsdsException("flaky")).when(hsdsClient).setDomainAcl(anyString(), anyString(), any(), any(), any());
    bridge.onPermissionsChanged(new PermissionsChangedEvent(30L, "HdfContainer", "a", PermissionsChangedEvent.Kind.UPDATED));
    assertEquals(1, bridge.retrySnapshot().size());

    // Now HSDS recovers — drain should succeed.
    doNothing().when(hsdsClient).setDomainAcl(anyString(), anyString(), any(), any(), any());
    int drained = bridge.drainRetries();
    assertEquals(1, drained);
    assertEquals(0, bridge.retrySnapshot().size());
  }

  @Test
  void featureOffSkipsSilently() {
    when(hsdsInstance.isUnsatisfied()).thenReturn(true);
    var ev = new PermissionsChangedEvent(50L, "HdfContainer", "appid-50", PermissionsChangedEvent.Kind.UPDATED);
    bridge.onPermissionsChanged(ev);
    verify(dao, never()).findByNeo4jId(anyLong());
    verify(hsdsClient, never()).setDomainAcl(anyString(), anyString(), any(), any(), any());
  }
}
