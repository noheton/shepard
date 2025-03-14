package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.semantic.ISemanticRepositoryConnector;
import de.dlr.shepard.context.semantic.SemanticRepositoryConnectorFactory;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.SemanticRepositoryDAO;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SemanticRepositoryServiceTest {

  @InjectMock
  SemanticRepositoryDAO semanticRepositoryDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  @InjectMock
  ISemanticRepositoryConnector semanticRepositoryConnector;

  @Inject
  SemanticRepositoryService service;

  @BeforeEach
  public void setUpRepositories() {
    when(
      semanticRepositoryConnectorFactory.getRepositoryService(SemanticRepositoryType.SPARQL, "http://test.org")
    ).thenReturn(semanticRepositoryConnector);
  }

  @Test
  public void getAllRepositoriesTest() {
    var expected = List.of(new SemanticRepository(1L));

    when(semanticRepositoryDAO.findAllSemanticRepositories(null)).thenReturn(expected);
    var actual = service.getAllRepositories(null);

    assertEquals(expected, actual);
  }

  @Test
  public void getAllRepositoriesTest_pagination() {
    QueryParamHelper params = new QueryParamHelper();
    params = params.withName("name");
    var expected = List.of(new SemanticRepository(1L));
    when(semanticRepositoryDAO.findAllSemanticRepositories(params)).thenReturn(expected);
    var actual = service.getAllRepositories(params);
    assertEquals(expected, actual);
  }

  @Test
  public void getRepositoryTest() {
    var expected = new SemanticRepository(1L);

    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(expected);
    var actual = service.getRepository(1L);

    assertEquals(expected, actual);
  }

  @Test
  public void getRepositoryTest_isNull() {
    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(null);
    var actual = service.getRepository(1L);

    assertNull(actual);
  }

  @Test
  public void getRepositoryTest_isDeleted() {
    var expected = new SemanticRepository(1L);
    expected.setDeleted(true);

    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(expected);
    var actual = service.getRepository(1L);

    assertNull(actual);
  }

  @Test
  public void createRepositoryTest() {
    var user = new User("bob");
    var date = new Date();
    var input = new SemanticRepositoryIO() {
      {
        setEndpoint("http://test.org");
        setName("Name");
        setType(SemanticRepositoryType.SPARQL);
      }
    };
    var toCreate = new SemanticRepository() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("http://test.org");
        setName("Name");
        setType(SemanticRepositoryType.SPARQL);
      }
    };
    var expected = new SemanticRepository() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("http://test.org");
        setName("Name");
        setType(SemanticRepositoryType.SPARQL);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryConnector.healthCheck()).thenReturn(true);
    when(semanticRepositoryDAO.createOrUpdate(toCreate)).thenReturn(expected);

    var actual = service.createRepository(input, "bob");
    assertEquals(expected, actual);
  }

  @Test
  public void createRepositoryTest_malformedUrl() {
    var user = new User("bob");
    var date = new Date();
    var input = new SemanticRepositoryIO() {
      {
        setEndpoint("wrong");
        setName("Name");
        setType(SemanticRepositoryType.SPARQL);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);

    assertThrows(InvalidBodyException.class, () -> service.createRepository(input, "bob"));
  }

  @Test
  public void createRepositoryTest_healthCheckFailed() {
    var user = new User("bob");
    var date = new Date();
    var input = new SemanticRepositoryIO() {
      {
        setEndpoint("http://test.org");
        setName("Name");
        setType(SemanticRepositoryType.SPARQL);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryConnector.healthCheck()).thenReturn(false);

    assertThrows(InvalidBodyException.class, () -> service.createRepository(input, "bob"));
  }

  @Test
  public void deleteRepositoryTest() {
    var user = new User("bob");
    var date = new Date();
    var repository = new SemanticRepository(1L);

    var expected = new SemanticRepository(1L);
    expected.setDeleted(true);
    expected.setUpdatedBy(user);
    expected.setUpdatedAt(date);

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(repository);

    var actual = service.deleteRepository(1L, "bob");
    assertTrue(actual);
    verify(semanticRepositoryDAO).createOrUpdate(expected);
  }

  @Test
  public void deleteRepositoryTest_repositoryIsNull() {
    var user = new User("bob");
    var date = new Date();

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteRepository(1L, "bob");
    assertFalse(actual);
  }
}
