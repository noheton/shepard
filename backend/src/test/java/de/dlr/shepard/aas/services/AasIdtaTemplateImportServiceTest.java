package de.dlr.shepard.aas.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.v2.aas.io.AasIdtaImportResultIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link AasIdtaTemplateImportService} (AAS1d). */
class AasIdtaTemplateImportServiceTest {

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  TemplateBodyValidator bodyValidator;

  AasIdtaTemplateImportService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new AasIdtaTemplateImportService();
    service.templateDAO = templateDAO;
    service.bodyValidator = bodyValidator;
    // bodyValidator.validate() is void — default Mockito mock is a no-op
    when(templateDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(templateDAO.findLatestByName(any(), any())).thenReturn(Optional.empty());
  }

  // --- classpath loading ---

  @Test
  void bundledYamlLoadsThreeEntries() {
    // Use the real TemplateBodyValidator to verify the bodies parse correctly.
    service.bodyValidator = new TemplateBodyValidator();

    AasIdtaImportResultIO result = service.importBundledTemplates("system");

    assertEquals(3, result.getCreated().size());
    assertEquals(0, result.getSkipped());
  }

  @Test
  void bundledTemplateNamesMatchExpected() {
    AasIdtaImportResultIO result = service.importBundledTemplates("system");

    List<String> names = result.getCreated().stream()
        .map(ShepardTemplateIO::getName)
        .sorted()
        .toList();
    assertEquals(List.of("IDTA Digital Nameplate", "IDTA Technical Data", "IDTA Time Series Data"), names);
  }

  @Test
  void bundledTemplatesHaveCorrectKind() {
    AasIdtaImportResultIO result = service.importBundledTemplates("system");

    result.getCreated().forEach(t ->
        assertEquals(AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND, t.getTemplateKind()));
  }

  // --- idempotency: skip when identical ---

  @Test
  void skipsTemplateWhenBodyDescriptionTagsAreIdentical() {
    // First run — creates all 3 (no prior records).
    AasIdtaImportResultIO firstRun = service.importBundledTemplates("system");
    assertEquals(3, firstRun.getCreated().size());
    // 3 createOrUpdate calls so far

    // Wire findLatestByName to return each template as it now "exists",
    // with identical body/description/tags to what was just imported.
    firstRun.getCreated().forEach(io -> {
      ShepardTemplate existing = new ShepardTemplate(io.getName(), io.getTemplateKind(), io.getBody());
      existing.setDescription(io.getDescription());
      existing.setTags(io.getTags() != null ? io.getTags() : List.of());
      when(templateDAO.findLatestByName(io.getName(), io.getTemplateKind()))
          .thenReturn(Optional.of(existing));
    });

    // Second run — everything is identical, all 3 should be skipped.
    AasIdtaImportResultIO secondRun = service.importBundledTemplates("system");

    assertEquals(0, secondRun.getCreated().size());
    assertEquals(3, secondRun.getSkipped());
    // No createOrUpdate beyond the first run's 3
    verify(templateDAO, times(3)).createOrUpdate(any());
  }

  @Test
  void createsNewVersionWhenBodyDiffers() {
    String kind = AasServerSelfDescriptionService.AAS_SUBMODEL_TEMPLATE_KIND;

    // Simulate a stale Nameplate with different body.
    ShepardTemplate stale = new ShepardTemplate(
        "IDTA Digital Nameplate", kind, "{\"submodelElements\":[]}");
    stale.setVersion(1);
    stale.setDescription("old description");
    stale.setTags(List.of());
    when(templateDAO.findLatestByName("IDTA Digital Nameplate", kind))
        .thenReturn(Optional.of(stale));

    ShepardTemplate nextVersion = new ShepardTemplate();
    nextVersion.setVersion(2);
    nextVersion.setName("IDTA Digital Nameplate");
    nextVersion.setTemplateKind(kind);
    when(templateDAO.nextVersionOf(stale)).thenReturn(nextVersion);

    AasIdtaImportResultIO result = service.importBundledTemplates("system");

    // All 3 created (Nameplate bumped to v2; other two minted fresh).
    assertEquals(3, result.getCreated().size());
    assertEquals(0, result.getSkipped());
    // Nameplate: retire prior (1 call) + save new (1 call); others: 1 each = 5 total
    verify(templateDAO, times(5)).createOrUpdate(any());
  }

  @Test
  void stampsCallerOnCreatedTemplates() {
    AasIdtaImportResultIO result = service.importBundledTemplates("alice");

    // createdAt is set by the service (millis since epoch)
    result.getCreated().forEach(t -> assertNotNull(t.getCreatedAt()));
    // "alice" is a plain username (not UUID-shaped), so DisplayNameResolver passes it through
    result.getCreated().forEach(t -> assertEquals("alice", t.getCreatedBy()));
  }

  @Test
  void allBundledTemplatesVersionStartAtOne() {
    AasIdtaImportResultIO result = service.importBundledTemplates("system");

    result.getCreated().forEach(t -> assertEquals(1, t.getVersion()));
  }
}
