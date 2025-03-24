package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.RandomGenerator;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActivateRequestContext
public class AnnotatableTimeseriesServiceTest {

  private TimeseriesContainer container;
  private SemanticRepository semanticRepository;
  private final String iri = "http://purl.obolibrary.org/obo/uo.owl";

  @InjectMock
  SemanticAnnotationService semanticAnnotationService;

  @Inject
  TimeseriesContainerDAO timeseriesContainerDao;

  @Inject
  SemanticRepositoryDAO semanticRepositoryDao;

  @Inject
  AnnotatableTimeseriesService service;

  @BeforeAll
  @Transactional
  public void setup() {
    var timeseriesContainer = new TimeseriesContainer();
    timeseriesContainer.setName("ttc-" + RandomGenerator.generateString(10));
    container = timeseriesContainerDao.createOrUpdate(timeseriesContainer);

    semanticRepository = new SemanticRepository();
    semanticRepository.setName("ontobee test repository");
    semanticRepository.setEndpoint("https://sparql.hegroup.org/sparql/");
    semanticRepository.setType(SemanticRepositoryType.SPARQL);
    semanticRepositoryDao.createOrUpdate(semanticRepository);
  }

  @BeforeEach
  public void setupEach() {
    Mockito.doReturn("prop", "value").when(semanticAnnotationService).validateTerm(semanticRepository, iri);
  }

  @AfterAll
  @Transactional
  public void cleanup() {}

  @Test
  public void createAnnotation_NothingExists_AnnotatableTimeseriesAndAnnotationAreCreated() {
    long timeseriesId = 1;
    SemanticAnnotationIO annotationIO = new SemanticAnnotationIO();
    annotationIO.setPropertyRepositoryId(semanticRepository.getId());
    annotationIO.setPropertyIRI(iri);
    annotationIO.setValueRepositoryId(semanticRepository.getId());
    annotationIO.setValueIRI(iri);

    var actual = service.createAnnotation(container.getId(), timeseriesId, annotationIO);

    assertTrue(actual.getId() > 0);
    assertEquals("prop::value", actual.getName());
    assertEquals(iri, actual.getPropertyIRI());
    assertEquals(iri, actual.getValueIRI());
    assertEquals(semanticRepository, actual.getPropertyRepository());
    assertEquals(semanticRepository, actual.getValueRepository());
  }

  @Test
  public void getAnnotations_AnnotatableTimeseriesWithTwoAnnotationsExist_returnsTwoAnnotations() {
    long timeseriesId = 2;
    var firstAnnotation = new SemanticAnnotationIO();
    firstAnnotation.setPropertyRepositoryId(semanticRepository.getId());
    firstAnnotation.setPropertyIRI(iri);
    firstAnnotation.setValueRepositoryId(semanticRepository.getId());
    firstAnnotation.setValueIRI(iri);

    service.createAnnotation(container.getId(), timeseriesId, firstAnnotation);

    var secondAnnotation = new SemanticAnnotationIO();
    secondAnnotation.setPropertyRepositoryId(semanticRepository.getId());
    secondAnnotation.setPropertyIRI(iri);
    secondAnnotation.setValueRepositoryId(semanticRepository.getId());
    secondAnnotation.setValueIRI(iri);

    service.createAnnotation(container.getId(), timeseriesId, secondAnnotation);

    service.clearSession();
    var actual = service.getAnnotations(container.getId(), timeseriesId);

    assertEquals(2, actual.size());
    assertNotNull(actual.get(0).getPropertyRepository(), "Property repository is null");
    assertNotNull(actual.get(0).getValueRepository(), "Value repository is null");
  }

  @Test
  public void getAnnotations_AnnotatableTimeseriesDoesNotExist_returnsEmptyList() {
    long nonExistingContainerId = 9999999L;
    var actual = service.getAnnotations(nonExistingContainerId, 1L);

    assertNotNull(actual);
    assertEquals(0, actual.size());
  }

  @Test
  public void getAnnotationById_AnnotatableTimeseriesDoesExist_returnsAnnotatableTimeseriesWithAnnotation() {
    SemanticAnnotationIO annotationIO = new SemanticAnnotationIO();
    annotationIO.setPropertyRepositoryId(semanticRepository.getId());
    annotationIO.setPropertyIRI(iri);
    annotationIO.setValueRepositoryId(semanticRepository.getId());
    annotationIO.setValueIRI(iri);

    var storedEntity = service.createAnnotation(container.getId(), 4L, annotationIO);

    service.clearSession();
    var actual = service.getAnnotationById(container.getId(), storedEntity.getId());

    assertNotNull(actual);
    assertEquals("prop::value", actual.getName());
    assertEquals(annotationIO.getPropertyIRI(), actual.getPropertyIRI());
    assertEquals(annotationIO.getValueIRI(), actual.getValueIRI());
    assertEquals(semanticRepository.getId(), actual.getPropertyRepository().getId());
    assertEquals(semanticRepository.getId(), actual.getValueRepository().getId());
    assertEquals(storedEntity.getId(), actual.getId());
  }

  @Test
  public void getAnnotationById_AnnotationDoesNotExist_throwsNotFoundException() {
    long nonExistingId = 9999999L;

    assertThrows(NotFoundException.class, () -> service.getAnnotationById(container.getId(), nonExistingId));
  }

  @Test
  public void delete_AnnotatableTimeseriesDoesExist_entityIsDeleted() {
    SemanticAnnotationIO annotationIO = new SemanticAnnotationIO();
    annotationIO.setPropertyRepositoryId(semanticRepository.getId());
    annotationIO.setPropertyIRI(iri);
    annotationIO.setValueRepositoryId(semanticRepository.getId());
    annotationIO.setValueIRI(iri);

    var storedEntity = service.createAnnotation(container.getId(), 5L, annotationIO);

    service.clearSession();
    service.deleteAnnotation(storedEntity.getId());

    service.clearSession();
    assertThrows(NotFoundException.class, () -> service.getAnnotationById(container.getId(), storedEntity.getId()));
  }
}
