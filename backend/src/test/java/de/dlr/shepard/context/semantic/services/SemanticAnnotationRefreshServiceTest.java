package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.ISemanticRepositoryConnector;
import de.dlr.shepard.context.semantic.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.version.daos.VersionableEntityConcreteDAO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * N1l — unit tests for {@link SemanticAnnotationService#refreshStaleSnapshots()}.
 */
@QuarkusComponentTest
public class SemanticAnnotationRefreshServiceTest {

  @InjectMock
  SemanticAnnotationDAO semanticAnnotationDAO;

  @InjectMock
  VersionableEntityConcreteDAO concreteDAO;

  @InjectMock
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  @InjectMock
  SemanticRepositoryService semanticRepositoryService;

  @InjectMock
  ISemanticRepositoryConnector mockConnector;

  @Inject
  SemanticAnnotationService service;

  private SemanticRepository internalRepo;

  @BeforeEach
  public void setUp() {
    internalRepo = new SemanticRepository();
    internalRepo.setId(10L);
    internalRepo.setType(SemanticRepositoryType.INTERNAL);
    internalRepo.setEndpoint(null);

    when(
      semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.INTERNAL, null)
    ).thenReturn(mockConnector);
  }

  private SemanticAnnotation annotation(
    long id,
    String propIri,
    String propName,
    String valIri,
    String valName
  ) {
    SemanticAnnotation a = new SemanticAnnotation(id);
    a.setPropertyIRI(propIri);
    a.setPropertyName(propName);
    a.setPropertyRepository(internalRepo);
    a.setValueIRI(valIri);
    a.setValueName(valName);
    a.setValueRepository(internalRepo);
    return a;
  }

  @Test
  public void refreshStaleSnapshots_labelChanged_updatesAnnotation() {
    SemanticAnnotation ann = annotation(1L, "http://ex/prop", "Old Property", "http://ex/val", "Old Value");

    when(semanticAnnotationDAO.findPaginated(0, 500)).thenReturn(List.of(ann));
    when(semanticAnnotationDAO.findPaginated(500, 500)).thenReturn(Collections.emptyList());
    when(mockConnector.getTerm("http://ex/prop")).thenReturn(Map.of("en", "New Property"));
    when(mockConnector.getTerm("http://ex/val")).thenReturn(Map.of("en", "New Value"));

    int updated = service.refreshStaleSnapshots();

    assertEquals(1, updated);
    verify(semanticAnnotationDAO, times(1)).createOrUpdate(ann);
    assertEquals("New Property", ann.getPropertyName());
    assertEquals("New Value", ann.getValueName());
  }

  @Test
  public void refreshStaleSnapshots_labelUnchanged_noWrite() {
    SemanticAnnotation ann = annotation(2L, "http://ex/prop", "Same Label", "http://ex/val", "Same Val");

    when(semanticAnnotationDAO.findPaginated(0, 500)).thenReturn(List.of(ann));
    when(semanticAnnotationDAO.findPaginated(500, 500)).thenReturn(Collections.emptyList());
    when(mockConnector.getTerm("http://ex/prop")).thenReturn(Map.of("", "Same Label"));
    when(mockConnector.getTerm("http://ex/val")).thenReturn(Map.of("", "Same Val"));

    int updated = service.refreshStaleSnapshots();

    assertEquals(0, updated);
    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
  }

  @Test
  public void refreshStaleSnapshots_nullRepository_skipped() {
    SemanticAnnotation ann = new SemanticAnnotation(3L);
    ann.setPropertyIRI("http://ex/prop");
    ann.setPropertyName("Legacy Prop");
    ann.setPropertyRepository(null);
    ann.setValueIRI("http://ex/val");
    ann.setValueName("Legacy Val");
    ann.setValueRepository(null);

    when(semanticAnnotationDAO.findPaginated(0, 500)).thenReturn(List.of(ann));
    when(semanticAnnotationDAO.findPaginated(500, 500)).thenReturn(Collections.emptyList());

    int updated = service.refreshStaleSnapshots();

    assertEquals(0, updated);
    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
  }

  @Test
  public void refreshStaleSnapshots_connectorThrows_doesNotPropagate() {
    SemanticAnnotation bad = annotation(4L, "http://ex/bad", "Old Bad", "http://ex/val", "Old Val");
    SemanticAnnotation good = annotation(5L, "http://ex/good", "Old Good", "http://ex/val2", "Old Val2");

    when(semanticAnnotationDAO.findPaginated(0, 500)).thenReturn(List.of(bad, good));
    when(semanticAnnotationDAO.findPaginated(500, 500)).thenReturn(Collections.emptyList());

    when(mockConnector.getTerm("http://ex/bad")).thenThrow(new RuntimeException("n10s unavailable"));
    when(mockConnector.getTerm("http://ex/val")).thenThrow(new RuntimeException("n10s unavailable"));
    when(mockConnector.getTerm("http://ex/good")).thenReturn(Map.of("en", "New Good"));
    when(mockConnector.getTerm("http://ex/val2")).thenReturn(Map.of("en", "New Val2"));

    int updated = service.refreshStaleSnapshots();

    assertEquals(1, updated);
    verify(semanticAnnotationDAO, times(1)).createOrUpdate(good);
    verify(semanticAnnotationDAO, never()).createOrUpdate(bad);
  }

  @Test
  public void refreshStaleSnapshots_emptyGraph_returnsZero() {
    when(semanticAnnotationDAO.findPaginated(0, 500)).thenReturn(Collections.emptyList());

    int updated = service.refreshStaleSnapshots();

    assertEquals(0, updated);
    verify(semanticAnnotationDAO, never()).createOrUpdate(any());
  }
}
