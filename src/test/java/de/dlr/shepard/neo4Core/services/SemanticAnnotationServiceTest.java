package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.AbstractEntityDAO;
import de.dlr.shepard.neo4Core.dao.SemanticAnnotationDAO;
import de.dlr.shepard.neo4Core.dao.SemanticRepositoryDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.semantics.ISemanticRepositoryConnector;
import de.dlr.shepard.semantics.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.semantics.SemanticRepositoryType;
import de.dlr.shepard.util.DateHelper;

public class SemanticAnnotationServiceTest extends BaseTestCase {

	@Mock
	private SemanticAnnotationDAO semanticAnnotationDAO;

	@Mock
	private SemanticRepositoryDAO semanticRepositoryDAO;

	@Mock
	private AbstractEntityDAO abstractEntityDAO;

	@Mock
	private UserDAO userDAO;

	@Mock
	private DateHelper dateHelper;

	@Mock
	private SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

	@Mock
	private ISemanticRepositoryConnector propConnector;

	@Mock
	private ISemanticRepositoryConnector valConnector;

	@InjectMocks
	private SemanticAnnotationService service;

	private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

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

		when(propConnector.getTerm("propIri")).thenReturn(Map.of(RDFS_LABEL, "propObject"));
		when(valConnector.getTerm("valIri")).thenReturn(Map.of(RDFS_LABEL, "valObject"));
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
	public void getAnnotationTest_Deleted() {
		var expected = new SemanticAnnotation(1L);
		expected.setDeleted(true);
		when(semanticAnnotationDAO.find(1L)).thenReturn(expected);
		var actual = service.getAnnotation(1L);
		assertNull(actual);
	}

	@Test
	public void createAnnotationTest() {
		var user = new User("bob");
		var date = new Date();
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
				setName("propObject-valObject");
				setPropertyIRI("propIri");
				setPropertyRepository(propRepo);
				setValueIRI("valIri");
				setValueRepository(valRepo);
				setCreatedAt(date);
				setCreatedBy(user);
			}
		};
		var expected = new SemanticAnnotation() {
			{
				setId(1L);
				setName("propObject-valObject");
				setPropertyIRI("propIri");
				setPropertyRepository(propRepo);
				setValueIRI("valIri");
				setValueRepository(valRepo);
				setCreatedAt(date);
				setCreatedBy(user);
			}
		};
		var entity = new Collection(5L);
		var entityUpdated = new Collection(5L);
		entityUpdated.setAnnotations(List.of(expected));

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticAnnotationDAO.createOrUpdate(toCreate)).thenReturn(expected);

		var actual = service.createAnnotation(5L, annotation, "bob");
		assertEquals(expected, actual);
		verify(semanticAnnotationDAO).createOrUpdate(toCreate);
		verify(abstractEntityDAO).update(entityUpdated);
	}

	@Test
	public void createAnnotationTest_EntityNull() {
		var user = new User("bob");
		var date = new Date();
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void createAnnotationTest_EntityDeleted() {
		var user = new User("bob");
		var date = new Date();
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

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void createAnnotationTest_PropRepoNull() {
		var user = new User("bob");
		var date = new Date();

		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticRepositoryDAO.find(2L)).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void createAnnotationTest_PropRepoDeleted() {
		var user = new User("bob");
		var date = new Date();
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

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(semanticRepositoryDAO.find(2L)).thenReturn(propRepo);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void createAnnotationTest_InvalidPropTermNull() {
		var user = new User("bob");
		var date = new Date();
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(null);

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void createAnnotationTest_InvalidPropTermEmpty() {
		var user = new User("bob");
		var date = new Date();
		var annotation = new SemanticAnnotationIO() {
			{
				setPropertyIRI("propIri");
				setPropertyRepositoryId(2L);
				setValueIRI("valIri");
				setValueRepositoryId(3L);
			}
		};
		var entity = new Collection(5L);

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(abstractEntityDAO.find(5L)).thenReturn(entity);
		when(propConnector.getTerm("propIri")).thenReturn(Collections.emptyMap());

		assertThrows(InvalidBodyException.class, () -> service.createAnnotation(5L, annotation, "bob"));
	}

	@Test
	public void deleteAnnotationTest() {
		var user = new User("bob");
		var date = new Date();
		var annotation = new SemanticAnnotation(1L);
		var expected = new SemanticAnnotation(1L);
		expected.setDeleted(true);
		expected.setUpdatedAt(date);
		expected.setUpdatedBy(user);

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(semanticAnnotationDAO.find(1L)).thenReturn(annotation);

		var actual = service.deleteAnnotation(1L, "bob");
		assertTrue(actual);
		verify(semanticAnnotationDAO).createOrUpdate(expected);
	}

	@Test
	public void deleteAnnotationTest_isNull() {
		var user = new User("bob");
		var date = new Date();

		when(dateHelper.getDate()).thenReturn(date);
		when(userDAO.find("bob")).thenReturn(user);
		when(semanticAnnotationDAO.find(1L)).thenReturn(null);

		var actual = service.deleteAnnotation(1L, "bob");
		assertFalse(actual);
	}

}
