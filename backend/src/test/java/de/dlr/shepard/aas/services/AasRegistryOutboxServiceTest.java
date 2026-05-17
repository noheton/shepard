package de.dlr.shepard.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.aas.entities.AasRegistration;
import de.dlr.shepard.aas.entities.AasRegistration.Status;
import de.dlr.shepard.aas.services.AasRegistryClient.RegistrationResult;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.aas.io.AasShellDescriptorIO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AasRegistryOutboxServiceTest {

  static final String REGISTRY_URL = "https://registry.example.org";
  static final String BASE_URL = "https://shepard.example.org";
  static final String SHELL_APP_ID = "col-aaa-111";
  static final String SHELL_URN = "urn:shepard:collection:" + SHELL_APP_ID;

  @Mock
  AasRegistrationDAO registrationDAO;

  @Mock
  CollectionDAO collectionDAO;

  @Mock
  AasRegistryClient registryClient;

  AasRegistryOutboxService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AasRegistryOutboxService();
    service.registrationDAO = registrationDAO;
    service.collectionDAO = collectionDAO;
    service.registryClient = registryClient;
    service.registryUrl = Optional.of(REGISTRY_URL);
    service.registryApiKey = Optional.empty();
    service.baseUrl = Optional.of(BASE_URL);

    when(registrationDAO.listNonDeletedCollectionAppIds()).thenReturn(List.of());
    when(registrationDAO.listPendingOrFailed(anyString())).thenReturn(List.of());
  }

  // --- syncAll() guard conditions ---

  @Test
  void syncAllReturnsZeroWhenNoRegistryUrl() {
    service.registryUrl = Optional.empty();
    assertEquals(0, service.syncAll());
    verify(registrationDAO, never()).listNonDeletedCollectionAppIds();
  }

  @Test
  void syncAllReturnsZeroWhenRegistryUrlBlank() {
    service.registryUrl = Optional.of("  ");
    assertEquals(0, service.syncAll());
    verify(registrationDAO, never()).listNonDeletedCollectionAppIds();
  }

  @Test
  void syncAllReturnsZeroWhenNoCollectionsAndNoRows() {
    assertEquals(0, service.syncAll());
    verify(registrationDAO, times(1)).listNonDeletedCollectionAppIds();
  }

  // --- seedPendingRows() ---

  @Test
  void seedCreatesNewPendingRowForUnregisteredCollection() {
    when(registrationDAO.listNonDeletedCollectionAppIds()).thenReturn(List.of(SHELL_APP_ID));
    when(registrationDAO.findByShellAndRegistry(SHELL_APP_ID, REGISTRY_URL)).thenReturn(null);

    service.seedPendingRows(REGISTRY_URL);

    var captor = ArgumentCaptor.forClass(AasRegistration.class);
    verify(registrationDAO).createOrUpdate(captor.capture());
    AasRegistration created = captor.getValue();
    assertEquals(SHELL_APP_ID, created.getShellAppId());
    assertEquals(REGISTRY_URL, created.getRegistryUrl());
    assertEquals(Status.PENDING, created.getStatus());
    assertNotNull(created.getCreatedAt());
    assertNotNull(created.getUpdatedAt());
  }

  @Test
  void seedSkipsCollectionWithExistingRow() {
    var existing = new AasRegistration(1L);
    when(registrationDAO.listNonDeletedCollectionAppIds()).thenReturn(List.of(SHELL_APP_ID));
    when(registrationDAO.findByShellAndRegistry(SHELL_APP_ID, REGISTRY_URL)).thenReturn(existing);

    service.seedPendingRows(REGISTRY_URL);

    verify(registrationDAO, never()).createOrUpdate(any());
  }

  // --- pushPendingAndFailed() ---

  @Test
  void pushSyncsRowWhenRegistryReturnsSuccess() {
    Collection col = mockCollection(SHELL_APP_ID, "TestCollection");
    AasRegistration reg = pendingReg(SHELL_APP_ID);

    when(registrationDAO.listPendingOrFailed(REGISTRY_URL)).thenReturn(List.of(reg));
    when(collectionDAO.findByQuery(anyString(), any())).thenReturn(List.of(col));
    when(registryClient.register(eq(REGISTRY_URL), any(), any())).thenReturn(RegistrationResult.ok());

    int count = service.pushPendingAndFailed(REGISTRY_URL);

    assertEquals(1, count);
    assertEquals(Status.SYNCED, reg.getStatus());
    assertNull(reg.getErrorMessage());
  }

  @Test
  void pushMarksRowFailedWhenRegistryReturnsError() {
    Collection col = mockCollection(SHELL_APP_ID, "TestCollection");
    AasRegistration reg = pendingReg(SHELL_APP_ID);

    when(registrationDAO.listPendingOrFailed(REGISTRY_URL)).thenReturn(List.of(reg));
    when(collectionDAO.findByQuery(anyString(), any())).thenReturn(List.of(col));
    when(registryClient.register(eq(REGISTRY_URL), any(), any()))
      .thenReturn(RegistrationResult.fail("HTTP 503: Service Unavailable"));

    int count = service.pushPendingAndFailed(REGISTRY_URL);

    assertEquals(0, count);
    assertEquals(Status.FAILED, reg.getStatus());
    assertEquals("HTTP 503: Service Unavailable", reg.getErrorMessage());
  }

  @Test
  void pushMarksRowFailedWhenCollectionGone() {
    AasRegistration reg = pendingReg(SHELL_APP_ID);

    when(registrationDAO.listPendingOrFailed(REGISTRY_URL)).thenReturn(List.of(reg));
    when(collectionDAO.findByQuery(anyString(), any())).thenReturn(List.of());

    int count = service.pushPendingAndFailed(REGISTRY_URL);

    assertEquals(0, count);
    assertEquals(Status.FAILED, reg.getStatus());
    verify(registryClient, never()).register(any(), any(), any());
  }

  // --- buildDescriptor() ---

  @Test
  void buildDescriptorSetsCorrectShellId() {
    Collection col = mockCollection(SHELL_APP_ID, "My Collection");
    AasShellDescriptorIO desc = service.buildDescriptor(col);
    assertEquals(SHELL_URN, desc.id());
  }

  @Test
  void buildDescriptorSanitisesIdShort() {
    Collection col = mockCollection(SHELL_APP_ID, "My Collection-Name");
    AasShellDescriptorIO desc = service.buildDescriptor(col);
    assertEquals("My_Collection_Name", desc.idShort());
  }

  @Test
  void buildDescriptorIncludesEndpointWhenBaseUrlConfigured() {
    Collection col = mockCollection(SHELL_APP_ID, "Test");
    AasShellDescriptorIO desc = service.buildDescriptor(col);
    assertFalse(desc.endpoints().isEmpty());
    String href = desc.endpoints().get(0).protocolInformation().href();
    assertTrue(href.startsWith(BASE_URL), "href must start with base URL");
    assertTrue(href.contains(SHELL_APP_ID), "href must contain shell appId");
    assertEquals("AAS-3.1", desc.endpoints().get(0).interfaceName());
  }

  @Test
  void buildDescriptorEmptyEndpointsWhenNoBaseUrl() {
    service.baseUrl = Optional.empty();
    Collection col = mockCollection(SHELL_APP_ID, "Test");
    AasShellDescriptorIO desc = service.buildDescriptor(col);
    assertTrue(desc.endpoints().isEmpty());
  }

  // --- helpers ---

  private Collection mockCollection(String appId, String name) {
    var col = new Collection(42L);
    col.setAppId(appId);
    col.setName(name);
    return col;
  }

  private AasRegistration pendingReg(String shellAppId) {
    var reg = new AasRegistration(1L);
    reg.setShellAppId(shellAppId);
    reg.setRegistryUrl(REGISTRY_URL);
    reg.setStatus(Status.PENDING);
    reg.setCreatedAt(System.currentTimeMillis());
    reg.setUpdatedAt(System.currentTimeMillis());
    return reg;
  }
}
