package de.dlr.shepard.plugins.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.plugins.aas.v2.io.AasServerSelfDescriptionIO.RegistryRegistration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AasServerSelfDescriptionServiceTest {

  @Mock
  CollectionDAO collectionDAO;

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  AasRegistrationDAO registrationDAO;

  AasServerSelfDescriptionService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AasServerSelfDescriptionService();
    service.collectionDAO = collectionDAO;
    service.templateDAO = templateDAO;
    service.registrationDAO = registrationDAO;
    service.enabled = false;
    service.apiProfile = AasServerSelfDescriptionService.DEFAULT_API_PROFILE;
    service.registryUrl = Optional.empty();
    when(collectionDAO.countAll()).thenReturn(0L);
    when(registrationDAO.distinctSyncedRegistryUrls()).thenReturn(List.of());
  }

  @Test
  void disabledByDefault() {
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    var io = service.describe();
    assertFalse(io.isEnabled());
    assertEquals("Submodel-Repository-Read-3.1", io.getAasApiProfile());
    assertEquals(0L, io.getShellCount());
    assertFalse(io.getEndpoints().isEmpty());
    assertTrue(io.getSupportedSubmodelTemplates().isEmpty());
    assertTrue(io.getRegistryRegistrations().isEmpty());
  }

  @Test
  void enabledFlagPropagates() {
    service.enabled = true;
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    assertTrue(service.describe().isEnabled());
  }

  @Test
  void apiProfileFromConfig() {
    service.apiProfile = "Submodel-Repository-Write-3.1";
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    assertEquals("Submodel-Repository-Write-3.1", service.describe().getAasApiProfile());
  }

  @Test
  void shellCountReflectsCollectionCount() {
    when(collectionDAO.countAll()).thenReturn(42L);
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    assertEquals(42L, service.describe().getShellCount());
  }

  @Test
  void shellCountZeroWhenNoCollections() {
    when(collectionDAO.countAll()).thenReturn(0L);
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    assertEquals(0L, service.describe().getShellCount());
  }

  @Test
  void shellsEndpointAdvertised() {
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    var endpoints = service.describe().getEndpoints();
    assertTrue(endpoints.containsKey("shells"), "shells endpoint must be advertised");
    assertEquals("/v2/aas/shells", endpoints.get("shells"));
  }

  @Test
  void supportedTemplatesSourcedFromAasSubmodelTemplates() {
    // Service must filter by templateKind=AAS_SUBMODEL_TEMPLATE — other
    // kinds (COLLECTION_RECIPE etc.) leaking through this endpoint would be
    // a categorical bug (they're not AAS submodels).
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false))
      .thenReturn(
        List.of(
          new ShepardTemplate("nameplate-3-0", "AAS_SUBMODEL_TEMPLATE", "{\"submodel\":{}}"),
          new ShepardTemplate("technical-data-1-2", "AAS_SUBMODEL_TEMPLATE", "{\"submodel\":{}}")
        )
      );
    var io = service.describe();
    assertEquals(List.of("nameplate-3-0", "technical-data-1-2"), io.getSupportedSubmodelTemplates());
  }

  @Test
  void supportedTemplatesDedupedAndSorted() {
    // Multiple versions of the same template share the name; the well-known
    // doc reports each capability once, ordered for stable client diffing.
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false))
      .thenReturn(
        List.of(
          new ShepardTemplate("zeta", "AAS_SUBMODEL_TEMPLATE", "{\"submodel\":{}}"),
          new ShepardTemplate("alpha", "AAS_SUBMODEL_TEMPLATE", "{\"submodel\":{}}"),
          new ShepardTemplate("alpha", "AAS_SUBMODEL_TEMPLATE", "{\"submodel\":{}}")
        )
      );
    var io = service.describe();
    assertEquals(List.of("alpha", "zeta"), io.getSupportedSubmodelTemplates());
  }

  @Test
  void retiredTemplatesExcluded() {
    // The DAO call passes includeRetired=false; retired rows must not
    // appear in the supported list (capability advertising = current state only).
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    service.describe();
    org.mockito.Mockito.verify(templateDAO).list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false);
  }

  // --- AAS1-reg: registryRegistrations ---

  @Test
  void registryRegistrationsEmptyWhenNoConfigAndNoOutbox() {
    // No registry URL configured, no synced rows in outbox → empty list.
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    service.registryUrl = Optional.empty();
    assertTrue(service.describe().getRegistryRegistrations().isEmpty());
  }

  @Test
  void registryRegistrationsShowsConfiguredUrlWhenOutboxEmpty() {
    // Registry URL configured but outbox still empty (e.g. first boot before
    // sync completes) → advertise the configured URL so discovery works.
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    service.registryUrl = Optional.of("https://registry.example.org");
    var registrations = service.describe().getRegistryRegistrations();
    assertEquals(1, registrations.size());
    assertEquals("https://registry.example.org", registrations.get(0).getUrl());
    assertEquals("idta-registry", registrations.get(0).getKind());
  }

  @Test
  void registryRegistrationsPrefersSyncedOutboxOverConfig() {
    // When the outbox has synced rows, report those (not the config URL)
    // so the well-known reflects actual state, not just intent.
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    when(registrationDAO.distinctSyncedRegistryUrls())
      .thenReturn(List.of("https://registry.dlr.de"));
    service.registryUrl = Optional.of("https://registry.example.org");
    var registrations = service.describe().getRegistryRegistrations();
    assertEquals(1, registrations.size());
    assertEquals("https://registry.dlr.de", registrations.get(0).getUrl());
  }

  @Test
  void registryRegistrationsMultipleSyncedRegistries() {
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    when(registrationDAO.distinctSyncedRegistryUrls())
      .thenReturn(List.of("https://reg-a.example.org", "https://reg-b.example.org"));
    var registrations = service.describe().getRegistryRegistrations();
    assertEquals(2, registrations.size());
    assertEquals("idta-registry", registrations.get(0).getKind());
    assertEquals("idta-registry", registrations.get(1).getKind());
  }

  @Test
  void buildRegistrationsBlankUrlTreatedAsEmpty() {
    // Blank string from config (e.g. operator sets key but leaves value empty)
    // must not produce a registration entry — that would advertise an unusable URL.
    service.registryUrl = Optional.of("   ");
    var registrations = service.buildRegistrations();
    assertTrue(registrations.isEmpty());
  }

  @Test
  void buildRegistrationsReturnsList() {
    // buildRegistrations() is package-private and tested directly to
    // keep the well-known describe() tests focused on integration.
    when(registrationDAO.distinctSyncedRegistryUrls())
      .thenReturn(List.of("https://reg.example.org"));
    List<RegistryRegistration> result = service.buildRegistrations();
    assertNotNull(result);
    assertFalse(result.isEmpty());
  }
}
