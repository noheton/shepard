package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class SemanticRepositoryServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  FileBundleReferenceService fileReferenceService;

  @Inject
  VersionDAO versionDAO;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  StructuredDataService structuredDataService;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

  @Inject
  VersionService versionService;

  @Inject
  CollectionReferenceService collectionReferenceService;

  @Inject
  SemanticRepositoryService semanticRepositoryReferenceService;

  private String username = "user";
  private User user;
  private SemanticRepository rep1;
  private SemanticRepository rep2;
  private SemanticRepository repToDelete;

  @BeforeEach
  public void setUp() {
    if (user == null) {
      User user = new User(username);
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(username, "key"));
    }
    SemanticRepositoryIO repToDeleteIO = new SemanticRepositoryIO();
    repToDeleteIO.setName("repToDelete" + System.currentTimeMillis());
    repToDeleteIO.setType(SemanticRepositoryType.SPARQL);
    repToDeleteIO.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    repToDelete = semanticRepositoryService.createRepository(repToDeleteIO);
    SemanticRepositoryIO rep2ToCreate = new SemanticRepositoryIO();
    rep2ToCreate.setName("rep2" + System.currentTimeMillis());
    rep2ToCreate.setType(SemanticRepositoryType.SPARQL);
    rep2ToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    rep2 = semanticRepositoryService.createRepository(rep2ToCreate);
    SemanticRepositoryIO rep1ToCreate = new SemanticRepositoryIO();
    rep1ToCreate.setName("rep1" + System.currentTimeMillis());
    rep1ToCreate.setType(SemanticRepositoryType.SPARQL);
    rep1ToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    rep1 = semanticRepositoryService.createRepository(rep1ToCreate);
  }

  @Test
  @Transactional
  public void getAllRepositoriesTest() {
    QueryParamHelper params = new QueryParamHelper();
    List<SemanticRepository> allRepositories = semanticRepositoryService.getAllRepositories(params);
    assertEquals(true, allRepositories.contains(rep1));
    assertEquals(true, allRepositories.contains(rep2));
  }

  @Test
  @Transactional
  public void getRepositoryByName() {
    QueryParamHelper params = new QueryParamHelper();
    params = params.withName(rep1.getName());
    List<SemanticRepository> allRepositories = semanticRepositoryService.getAllRepositories(params);
    assertEquals(1, allRepositories.size());
    assertEquals(true, allRepositories.contains(rep1));
  }

  @Test
  @Transactional
  public void getRepositoriesWithPagination() {
    QueryParamHelper params0 = new QueryParamHelper();
    params0 = params0.withName(rep1.getName());
    params0 = params0.withPageAndSize(2, 2);
    QueryParamHelper params2 = new QueryParamHelper();
    params2 = params2.withPageAndSize(0, 2);
    QueryParamHelper params1 = new QueryParamHelper();
    params1 = params1.withPageAndSize(1, 1);
    List<SemanticRepository> allRepositories = semanticRepositoryService.getAllRepositories(params1);
    assertEquals(1, allRepositories.size());
    allRepositories = semanticRepositoryService.getAllRepositories(params2);
    assertEquals(2, allRepositories.size());
    allRepositories = semanticRepositoryService.getAllRepositories(params0);
    assertEquals(0, allRepositories.size());
  }

  @Test
  @Transactional
  public void getRepositoryById() {
    assertEquals(rep1, semanticRepositoryService.getRepository(rep1.getId()));
  }

  @Test
  @Transactional
  public void testDeleteRepository() {
    assertEquals(repToDelete, semanticRepositoryService.getRepository(repToDelete.getId()));
    semanticRepositoryService.deleteRepository(repToDelete.getId());
    InvalidPathException ex = assertThrows(InvalidPathException.class, () ->
      semanticRepositoryService.getRepository(repToDelete.getId())
    );
    assertEquals(
      "ID ERROR - Semantic Repository with id " + repToDelete.getId() + " is null or deleted",
      ex.getMessage()
    );
  }

  @Test
  @Transactional
  public void createRepositoryMalformedURL() {
    SemanticRepositoryIO repMalformedURL = new SemanticRepositoryIO();
    repMalformedURL.setName("repMalformedURL");
    repMalformedURL.setType(SemanticRepositoryType.SPARQL);
    repMalformedURL.setEndpoint("noEndpoint");
    InvalidBodyException ex = assertThrows(InvalidBodyException.class, () ->
      semanticRepositoryService.createRepository(repMalformedURL)
    );
    assertEquals("Invalid endpoint", ex.getMessage());
  }

  @Test
  @Transactional
  public void createRepositoryHealthCheckFailed() {
    SemanticRepositoryIO repMalformedURL = new SemanticRepositoryIO();
    repMalformedURL.setName("repMalformedURL");
    repMalformedURL.setType(SemanticRepositoryType.SPARQL);
    repMalformedURL.setEndpoint("http://test.org");
    Exception ex = assertThrows(Exception.class, () -> semanticRepositoryService.createRepository(repMalformedURL));
  }
}
