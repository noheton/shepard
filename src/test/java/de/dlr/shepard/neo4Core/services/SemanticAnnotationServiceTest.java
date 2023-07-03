package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.BasicEntityDAO;
import de.dlr.shepard.neo4Core.dao.SemanticAnnotationDAO;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.semantics.ISemanticRepositoryConnector;
import de.dlr.shepard.semantics.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.semantics.SemanticRepositoryType;

public class SemanticAnnotationServiceTest extends BaseTestCase {

	@Mock
	private SemanticAnnotationDAO semanticAnnotationDAO;

	@Mock
	private SemanticRepositoryDAO semanticRepositoryDAO;

	@Mock
	private BasicEntityDAO abstractEntityDAO;

	@Mock
	private SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

	@Mock
	private ISemanticRepositoryConnector propConnector;

	@Mock
	private ISemanticRepositoryConnector valConnector;

	@InjectMocks
	private SemanticAnnotationService service;

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
		when(semanticRepositoryDAO.find(2L)).thenReturn(propRepo);
		when(semanticRepositoryDAO.find(3L)).thenReturn(valRepo);

		when(semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.SKOSMOS, "propEndpoint"))
				.thenReturn(propConnector);
		when(semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.JSKOS, "valEndpoint"))
				.thenReturn(valConnector);

		when(propConnector.getTerm("propIri")).thenReturn(Map.of("", "propObject"));
		when(valConnector.getTerm("valIri")).thenReturn(Map.of("", "valObject"));
	}

	@Test
	public void getAllAnnotationsTest() {
		var expected = List.of(new SemanticAnnotation(1L));
		when(semanticAnnotationDAO.findAllSemanticAnnotations(2L)).thenReturn(expected);
		var actual = service.getAllAnnotations(2L);
		assertEquals(expected, actual);
	}

	@Test
	public void getAnnotationTest() {
		var expected = new SemanticAnnotation(1L);
		when(semanticAnnotationDAO.find(1L)).thenReturn(expected);
		var actual = service.getAnnotation(1L);
		assertEquals(expected, actual);
	}

	@Test
	public void getAnnotationTest_Null() {
		when(semanticAnnotationDAO.find(1L)).thenReturn(null);
		var actual = service.getAnnotation(1L);
		assertNull(actual);
	}

	@Test
	public void createAnnotationTest() {
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
		var entityUpdated = new Collection(5L);
		entityUpdated.setAnnotations(List.of(expected));

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticAnnotationDAO.createOrUpdate(toCreate)).thenReturn(expected);

		var actual = service.createAnnotation(5L, annotation);
		assertEquals(expected, actual);
		verify(semanticAnnotationDAO).createOrUpdate(toCreate);
		verify(abstractEntityDAO).update(entityUpdated);
	}

	@Test
	public void createAnnotationTest_EntityNull() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};

		when(abstractEntityDAO.find(5L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_EntityDeleted() {
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

		when(abstractEntityDAO.find(5L)).thenReturn(entity);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_PropRepoNull() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticRepositoryDAO.find(2L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_PropRepoDeleted() {
		var propRepo = new SemanticRepository() {
			{
				setId(2L);
				setType(SemanticRepositoryType.SKOSMOS);
				setEndpoint("propEndpoint");
				setDeleted(true);
			}
		};
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticRepositoryDAO.find(2L)).thenReturn(propRepo);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_InvalidPropTermNull() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_InvalidPropTermEmpty() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(Collections.emptyMap());

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation));
	}

	@Test
	public void createAnnotationTest_FirstLabel() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(Map.of("de", "Eigenschaft"));
		when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(i -> i.getArguments()[0]);

		var actual = service.createAnnotation(5L, annotation);
		assertEquals("Eigenschaft::valObject", actual.getName());

	}

	@Test
	public void createAnnotationTest_EnglishLabel() {
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(Map.of("de", "Eigenschaft", "en", "Property"));
		when(semanticAnnotationDAO.createOrUpdate(any())).thenAnswer(i -> i.getArguments()[0]);

		var actual = service.createAnnotation(5L, annotation);
		assertEquals("Property::valObject", actual.getName());
	}

	@Test
	public void deleteAnnotationTest() {
		when(semanticAnnotationDAO.delete(1L)).thenReturn(true);

		var actual = service.deleteAnnotation(1L);
		assertTrue(actual);
	}

	@Test
	public void deleteAnnotationTest_isNull() {
		when(semanticAnnotationDAO.delete(1L)).thenReturn(false);

		var actual = service.deleteAnnotation(1L);
		assertFalse(actual);
	}

}
