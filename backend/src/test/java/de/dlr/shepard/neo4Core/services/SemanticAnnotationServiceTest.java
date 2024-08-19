package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.BasicEntityDAO;
import de.dlr.shepard.neo4Core.dao.SemanticAnnotationDAO;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.dao.VersionableEntityConcreteDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.semantics.ISemanticRepositoryConnector;
import de.dlr.shepard.semantics.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.semantics.SemanticRepositoryType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SemanticAnnotationServiceTest {

  @InjectMock
  SemanticAnnotationDAO semanticAnnotationDAO;

  @InjectMock
  SemanticRepositoryDAO semanticRepositoryDAO;

  @InjectMock
  VersionableEntityConcreteDAO concreteDAO;

  @InjectMock
  BasicEntityDAO abstractEntityDAO;

  @InjectMock
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  @InjectMock
  ISemanticRepositoryConnector propConnector;

  @InjectMock
  ISemanticRepositoryConnector valConnector;

  @Inject
  SemanticAnnotationService service;

  private SemanticRepository propRepo = new SemanticRepository() {
    {
      setId(2L);
      setType(SemanticRepositoryType.SKOSMOS);
      setEndpoint("propEndpoint");
    }
  };
  private SemanticRepository valRepo = new SemanticRepository() {
    {
      setId(3L);
      setType(SemanticRepositoryType.JSKOS);
      setEndpoint("valEndpoint");
    }
  };

  @BeforeEach
  public void setUpRepositories() {
    when(semanticRepositoryDAO.findByNeo4jId(2L)).thenReturn(propRepo);
    when(semanticRepositoryDAO.findByNeo4jId(3L)).thenReturn(valRepo);

    when(
      semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.SKOSMOS, "propEndpoint")
    ).thenReturn(propConnector);
    when(
      semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.JSKOS, "valEndpoint")
    ).thenReturn(valConnector);

    when(propConnector.getTerm("propIri")).thenReturn(Map.of("", "propObject"));
    when(valConnector.getTerm("valIri")).thenReturn(Map.of("", "valObject"));
  }

  @Test
  public void getAllAnnotationsTest() {
    var expected = List.of(new SemanticAnnotation(1L));
    when(semanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId(2L)).thenReturn(expected);
    var actual = service.getAllAnnotationsByNeo4jId(2L);
    assertEquals(expected, actual);
  }

  @Test
  public void getAllAnnotationsByShepardIdTest() {
    var expected = List.of(new SemanticAnnotation(1L));
    when(semanticAnnotationDAO.findAllSemanticAnnotationsByShepardId(2L)).thenReturn(expected);
    var actual = service.getAllAnnotationsByShepardId(2L);
    assertEquals(expected, actual);
  }

  @Test
  public void getAnnotationTest() {
    var expected = new SemanticAnnotation(1L);
    when(semanticAnnotationDAO.findByNeo4jId(1L)).thenReturn(expected);
    var actual = service.getAnnotationByNeo4jId(1L);
    assertEquals(expected, actual);
  }

  @Test
  public void getAnnotationTest_Null() {
    when(semanticAnnotationDAO.findByNeo4jId(1L)).thenReturn(null);
    var actual = service.getAnnotationByNeo4jId(1L);
    assertNull(actual);
  }

  @Test
  public void createAnnotationByShepardIdTest() {
    var annotation = new SemanticAnnotationIO() {
      {
        setPropertyIRI("propIri");
        setPropertyRepositoryId(2L);
        setValueIRI("valIri");
        setValueRepositoryId(3L);
      }
    };
    var toCreate = new SemanticAnnotation() {
      {
        setName("propObject::valObject");
        setPropertyIRI("propIri");
        setPropertyRepository(propRepo);
        setValueIRI("valIri");
        setValueRepository(valRepo);
      }
    };
    var expected = new SemanticAnnotation() {
      {
        setId(1L);
        setName("propObject::valObject");
        setPropertyIRI("propIri");
        setPropertyRepository(propRepo);
        setValueIRI("valIri");
        setValueRepository(valRepo);
      }
    };
    var entity = new Collection(5L);
    entity.setShepardId(6L);
    var entityUpdated = new Collection(5L);
    entityUpdated.setShepardId(6L);
    entityUpdated.setAnnotations(List.of(expected));

    when(concreteDAO.findByShepardId(entity.getShepardId())).thenReturn(entity);
    when(semanticAnnotationDAO.createOrUpdate(toCreate)).thenReturn(expected);

    var actual = service.createAnnotationByShepardId(entity.getShepardId(), annotation);
    assertEquals(expected, actual);
    verify(semanticAnnotationDAO).createOrUpdate(toCreate);
    verify(concreteDAO).createOrUpdate(entity);
  }

  @Test
  public void createAnnotationByShepardIdTest_EntityNull() {
    var annotation = new SemanticAnnotationIO() {
      {
        setPropertyIRI("propIri");
        setPropertyRepositoryId(2L);
        setValueIRI("valIri");
        setValueRepositoryId(3L);
      }
    };

    when(concreteDAO.findByNeo4jId(5L)).thenReturn(null);

    assertThrows(InvalidBodyException.class, () -> service.createAnnotationByShepardId(5L, annotation));
  }

  @Test
  public void createAnnotationByShepardIdTest_EntityDeleted() {
    var annotation = new SemanticAnnotationIO() {
      {
        setPropertyIRI("propIri");
        setPropertyRepositoryId(2L);
        setValueIRI("valIri");
        setValueRepositoryId(3L);
      }
    };
    var entity = new Collection(5L);
    entity.setDeleted(true);

    when(concreteDAO.findByShepardId(5L)).thenReturn(entity);

    assertThrows(InvalidBodyException.class, () -> service.createAnnotationByShepardId(5L, annotation));
  }

  @Test
  public void deleteAnnotationTest() {
    when(semanticAnnotationDAO.deleteByNeo4jId(1L)).thenReturn(true);

    var actual = service.deleteAnnotationByNeo4jId(1L);
    assertTrue(actual);
  }

  @Test
  public void deleteAnnotationTest_isNull() {
    when(semanticAnnotationDAO.deleteByNeo4jId(1L)).thenReturn(false);

    var actual = service.deleteAnnotationByNeo4jId(1L);
    assertFalse(actual);
  }
}
