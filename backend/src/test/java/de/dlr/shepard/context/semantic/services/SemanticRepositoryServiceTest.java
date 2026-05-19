package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
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
  DateHelper dateHelper;

  @InjectMock
  UserService userService;

  @InjectMock
  SemanticRepositoryConnectorFactory semanticRepositoryConnectorFactory;

  @InjectMock
  ISemanticRepositoryConnector semanticRepositoryConnector;

  @Inject
  SemanticRepositoryService service;

  private final User user = new User("Testuser");

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
  public void getAllRepositoryByName() {
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

    var ex = assertThrows(InvalidPathException.class, () -> service.getRepository(1L));
    assertEquals("ID ERROR - Semantic Repository with id 1 is null or deleted", ex.getMessage());
  }

  @Test
  public void getRepositoryTest_isDeleted() {
    var expected = new SemanticRepository(1L);
    expected.setDeleted(true);

    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(expected);

    var ex = assertThrows(InvalidPathException.class, () -> service.getRepository(1L));
    assertEquals("ID ERROR - Semantic Repository with id 1 is null or deleted", ex.getMessage());
  }

  @Test
  public void createRepositoryTest() {
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

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryConnector.healthCheck()).thenReturn(true);
    when(semanticRepositoryDAO.createOrUpdate(toCreate)).thenReturn(expected);

    var actual = service.createRepository(input);
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

    when(dateHelper.getDate()).thenReturn(date);
    when(userService.getCurrentUser()).thenReturn(user);

    assertThrows(InvalidBodyException.class, () -> service.createRepository(input));
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

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryConnector.healthCheck()).thenReturn(false);

    assertThrows(InvalidBodyException.class, () -> service.createRepository(input));
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

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(repository);

    assertDoesNotThrow(() -> service.deleteRepository(1L));
    verify(semanticRepositoryDAO).createOrUpdate(expected);
  }

  @Test
  public void deleteRepositoryTest_repositoryIsNull() {
    var user = new User("bob");
    var date = new Date();

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.deleteRepository(1L));
  }

  @Test
  public void createRepositoryTest_internalType_skipsUrlValidation() {
    var date = new Date();
    var input = new SemanticRepositoryIO() {
      {
        setEndpoint("");
        setName("Built-in Semantic Store (n10s)");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };
    var toCreate = new SemanticRepository() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("");
        setName("Built-in Semantic Store (n10s)");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };
    var expected = new SemanticRepository() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("");
        setName("Built-in Semantic Store (n10s)");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.createOrUpdate(toCreate)).thenReturn(expected);

    // Must not throw — INTERNAL type bypasses URL parsing and health-check.
    var actual = assertDoesNotThrow(() -> service.createRepository(input));
    assertEquals(expected, actual);
  }

  @Test
  public void createRepositoryTest_internalType_withMalformedEndpoint_doesNotThrow() {
    var date = new Date();
    var input = new SemanticRepositoryIO() {
      {
        setEndpoint("not-a-url");
        setName("Internal");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };
    var toCreate = new SemanticRepository() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("not-a-url");
        setName("Internal");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };
    var expected = new SemanticRepository() {
      {
        setId(2L);
        setCreatedAt(date);
        setCreatedBy(user);
        setEndpoint("not-a-url");
        setName("Internal");
        setType(SemanticRepositoryType.INTERNAL);
      }
    };

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(semanticRepositoryDAO.createOrUpdate(toCreate)).thenReturn(expected);

    // Malformed endpoint is harmless for INTERNAL — connector ignores it entirely.
    assertDoesNotThrow(() -> service.createRepository(input));
  }
}
