package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticRepositoryService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class DataObjectSearchServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  ReferenceSearchService referenceSearcher;

  @Inject
  DataObjectSearchService dataObjectSearcher;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  AnnotatableTimeseriesService annotatableTimeseriesService;

  @Inject
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserService userService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  private Collection collection1;
  private DataObject dataObjectc1d1;
  private DataObject dataObjectc1d1succ1;
  private DataObject dataObjectc1d1succ2;

  private User user;
  private String username = "username";

  @BeforeEach
  public void setUp() {
    //create user
    if (user == null) {
      User user = new User(username);
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(username, "key"));
    }
    //create collection
    if (collection1 == null) {
      CollectionIO collection1IO = new CollectionIO();
      collection1IO.setName("collection1" + System.currentTimeMillis());
      collection1 = collectionService.createCollection(collection1IO);
    }
    //create dataobjectc1d1
    if (dataObjectc1d1 == null) {
      DataObjectIO dataObjectc1d1IO = new DataObjectIO();
      dataObjectc1d1IO.setName("c1do1" + System.currentTimeMillis());
      dataObjectc1d1 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d1IO);
    }
    //create dataobjectc1d1succ1
    if (dataObjectc1d1succ1 == null) {
      DataObjectIO dataObjectc1d1succ1IO = new DataObjectIO();
      dataObjectc1d1succ1IO.setName("c1do1qsucc1" + System.currentTimeMillis());
      long[] predecessorIds = { dataObjectc1d1.getShepardId() };
      dataObjectc1d1succ1IO.setPredecessorIds(predecessorIds);
      dataObjectc1d1succ1 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d1succ1IO);
    }
    //create dataobjectc1d1succ2
    if (dataObjectc1d1succ2 == null) {
      DataObjectIO dataObjectc1d1succ2IO = new DataObjectIO();
      dataObjectc1d1succ2IO.setName("c1do1qsucc2" + System.currentTimeMillis());
      dataObjectc1d1succ2IO.setParentId(dataObjectc1d1succ1.getId());
      long[] predecessorIds = { dataObjectc1d1.getShepardId() };
      dataObjectc1d1succ2IO.setPredecessorIds(predecessorIds);
      dataObjectc1d1succ2 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d1succ2IO);
    }
  }

  @Test
  @Transactional
  public void DONeighborhoodSearch() {
    //test successorIds contains
    SearchScope scope = new SearchScope();
    scope.setCollectionId(collection1.getShepardId());
    TraversalRules[] rules = {};
    scope.setTraversalRules(rules);
    SearchScope[] scopes = { scope };
    long[] ids = { dataObjectc1d1succ1.getId(), dataObjectc1d1succ2.getId() };
    String query =
      "{\"property\": \"successorIds\", \"value\": " + Arrays.toString(ids) + ", \"operator\": \"contains\"}";
    SearchBody body = new SearchBody();
    body.setScopes(scopes);
    SearchParams params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    ResponseBody response = dataObjectSearcher.search(body);
    assertEquals(1, response.getResults().length);
    assertEquals(dataObjectc1d1.getId(), response.getResults()[0].getId());
    //test successorIds contains strict superset
    long[] ids1 = { dataObjectc1d1succ1.getId(), dataObjectc1d1succ2.getId(), 0 };
    query = "{\"property\": \"successorIds\", \"value\": " + Arrays.toString(ids1) + ", \"operator\": \"contains\"}";
    body = new SearchBody();
    body.setScopes(scopes);
    params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    response = dataObjectSearcher.search(body);
    assertEquals(0, response.getResults().length);
    //test successorIds isContainedIn
    query =
      "{\"property\": \"successorIds\", \"value\": [" +
      dataObjectc1d1succ1.getId() +
      "], \"operator\": \"isContainedIn\"}";
    body = new SearchBody();
    body.setScopes(scopes);
    params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    response = dataObjectSearcher.search(body);
    assertEquals(2, response.getResults().length);
    HashSet<Long> idSetExpected = new HashSet<Long>();
    idSetExpected.add(dataObjectc1d1succ1.getId());
    idSetExpected.add(dataObjectc1d1succ2.getId());
    HashSet<Long> idSetFound = new HashSet<Long>();
    idSetFound.add(response.getResults()[0].getId());
    idSetFound.add(response.getResults()[1].getId());
    assertEquals(idSetExpected, idSetFound);
    //test predecessorsIds equals
    long[] ids2 = { dataObjectc1d1.getId() };
    query = "{\"property\": \"predecessorIds\", \"value\": " + Arrays.toString(ids2) + ", \"operator\": \"eq\"}";
    body = new SearchBody();
    body.setScopes(scopes);
    params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    response = dataObjectSearcher.search(body);
    assertEquals(2, response.getResults().length);
    idSetExpected.clear();
    idSetFound.clear();
    idSetExpected.add(dataObjectc1d1succ1.getId());
    idSetExpected.add(dataObjectc1d1succ2.getId());
    idSetFound.add(response.getResults()[0].getId());
    idSetFound.add(response.getResults()[1].getId());
    assertEquals(idSetExpected, idSetFound);
    //test parentIds equals
    long[] ids3 = { dataObjectc1d1succ1.getId() };
    String childClause =
      "{\"property\": \"parentIds\", \"value\": " + Arrays.toString(ids3) + ", \"operator\": \"eq\"}";
    String idClause = "{\"property\": \"id\", \"value\": 0, \"operator\": \"ge\"}";
    query = "{\"AND\" : [" + childClause + "," + idClause + "]}";
    body = new SearchBody();
    body.setScopes(scopes);
    params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    response = dataObjectSearcher.search(body);
    assertEquals(1, response.getResults().length);
    assertEquals(dataObjectc1d1succ2.getId(), response.getResults()[0].getId());
    //test childrenIds equals emptyset
    query = "{\"property\": \"childrenIds\", \"value\": [], \"operator\": \"eq\"}";
    body = new SearchBody();
    body.setScopes(scopes);
    params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    response = dataObjectSearcher.search(body);
    assertEquals(2, response.getResults().length);
    idSetExpected.clear();
    idSetFound.clear();
    idSetExpected.add(dataObjectc1d1.getId());
    idSetExpected.add(dataObjectc1d1succ2.getId());
    idSetFound.add(response.getResults()[0].getId());
    idSetFound.add(response.getResults()[1].getId());
    assertEquals(idSetExpected, idSetFound);
  }
}
