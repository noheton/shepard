package de.dlr.shepard.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AasServerSelfDescriptionServiceTest {

  @Mock
  CollectionDAO collectionDAO;

  @Mock
  ShepardTemplateDAO templateDAO;

  AasServerSelfDescriptionService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AasServerSelfDescriptionService();
    service.collectionDAO = collectionDAO;
    service.templateDAO = templateDAO;
    service.enabled = false;
    service.apiProfile = AasServerSelfDescriptionService.DEFAULT_API_PROFILE;
    when(collectionDAO.countAll()).thenReturn(0L);
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
    service.describe();
    org.mockito.Mockito.verify(templateDAO).list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false);
  }

  @Test
  void registryRegistrationsEmptyUntilAas1RegShips() {
    when(templateDAO.list(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, false)).thenReturn(List.of());
    assertNotNull(service.describe().getRegistryRegistrations());
    assertTrue(service.describe().getRegistryRegistrations().isEmpty());
  }
}
