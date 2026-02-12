package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.ShepardParserException;
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
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticRepositoryService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class ReferenceSearchServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  ReferenceSearchService referenceSearcher;

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

  private static Collection collection1;
  private static DataObject dataObjectc1d1;
  private static TimeseriesReference annoReference;
  private static TimeseriesReference noAnnoReference;
  private static TimeseriesContainer tsCon;
  private static TimeseriesEntity timeseriesEntity1;
  private static TimeseriesEntity timeseriesEntity2;
  private static SemanticRepository repository;
  private static User user;
  private static SemanticAnnotationIO AnnoToCreate;
  private static String username = "username";
  private static SearchScope scope;
  private static SearchBody body;
  private static SearchParams params;
  private static String query;
  private static ResponseBody response;

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
    //create dataobject
    if (dataObjectc1d1 == null) {
      DataObjectIO dataObjectc1d1IO = new DataObjectIO();
      dataObjectc1d1IO.setName("c1do1" + System.currentTimeMillis());
      dataObjectc1d1 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d1IO);
    }
    //create timeseriesContainer with content
    if (tsCon == null) {
      //create container
      TimeseriesContainerIO tsConIO = new TimeseriesContainerIO();
      tsConIO.setName("container");
      tsCon = timeseriesContainerService.createContainer(tsConIO);
      //create two timeseries
      TimeseriesDataPoint dataPoint = new TimeseriesDataPoint(4l, true);
      List<TimeseriesDataPoint> points = new ArrayList<TimeseriesDataPoint>();
      points.add(dataPoint);
      Timeseries ts1 = new Timeseries("m1", "d1", "l1", "s1", "f1");
      Timeseries ts2 = new Timeseries("m2", "d2", "l2", "s2", "f2");
      timeseriesEntity1 = timeseriesService.saveDataPoints(tsCon.getId(), ts1, points);
      timeseriesEntity2 = timeseriesService.saveDataPoints(tsCon.getId(), ts2, points);
      //create annotation for first timeseries
      SemanticRepositoryIO repToCreate = new SemanticRepositoryIO();
      repToCreate.setName("SemanticRepository");
      repToCreate.setType(SemanticRepositoryType.SPARQL);
      repToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
      repository = semanticRepositoryService.createRepository(repToCreate);
      //create Reference on annotated Timeseries
      TimeseriesReferenceIO ref1IO = new TimeseriesReferenceIO();
      ref1IO.setName("refAnno");
      ref1IO.setStart(0l);
      ref1IO.setEnd(9l);
      List<Timeseries> timeseries1 = new ArrayList<Timeseries>();
      timeseries1.add(ts1);
      ref1IO.setTimeseries(timeseries1);
      ref1IO.setTimeseriesContainerId(tsCon.getId());
      annoReference = timeseriesReferenceService.createReference(
        collection1.getShepardId(),
        dataObjectc1d1.getShepardId(),
        ref1IO
      );
      //create Reference on timeseries without annotation
      TimeseriesReferenceIO ref2IO = new TimeseriesReferenceIO();
      ref2IO.setName("refWithoutAnno");
      ref2IO.setStart(0l);
      ref2IO.setEnd(9l);
      List<Timeseries> timeseries2 = new ArrayList<Timeseries>();
      timeseries2.add(ts2);
      ref2IO.setTimeseries(timeseries2);
      ref2IO.setTimeseriesContainerId(tsCon.getId());
      annoReference = timeseriesReferenceService.createReference(
        collection1.getShepardId(),
        dataObjectc1d1.getShepardId(),
        ref2IO
      );
    }
    if (AnnoToCreate == null) {
      AnnoToCreate = new SemanticAnnotationIO();
      AnnoToCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
      AnnoToCreate.setPropertyRepositoryId(repository.getId());
      AnnoToCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
      AnnoToCreate.setValueRepositoryId(repository.getId());
      semanticAnnotationService.createAnnotationByShepardId(annoReference.getId(), AnnoToCreate);
    }
    if (scope == null) scope = new SearchScope();
    if (body == null) {
      body = new SearchBody();
      TraversalRules[] rules = {};
      scope.setTraversalRules(rules);
    }
    if (params == null) params = new SearchParams();
  }

  @Test
  @Transactional
  public void findNoReference() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    SearchScope[] scopes = { scope };
    query = "{\"property\": \"hasAnnotation\", \"value\": \"ingre.*::Almon.*\", \"operator\": \"eq\"}";
    params.setQuery(query);
    body.setSearchParams(params);
    body.setScopes(scopes);
    response = referenceSearcher.search(body);
    assertEquals(0, response.getResults().length);
  }

  @Test
  @Transactional
  public void findExactlyOneReferenceByeIRIEquality() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    SearchScope[] scopes = { scope };
    query =
      "{\"property\": \"hasAnnotationIRI\", \"value\": \"http://dbpedia.org/ontology/ingredient::http://dbpedia.org/resource/Almond_milk\", \"operator\": \"eq\"}";
    params.setQuery(query);
    body.setSearchParams(params);
    body.setScopes(scopes);
    response = referenceSearcher.search(body);
    assertEquals(1, response.getResults().length);
    assertEquals(annoReference.getId(), response.getResultSet()[0].getReferenceId());
  }

  @Test
  @Transactional
  public void findByPropertyIRIContains() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    SearchScope[] scopes = { scope };
    query = "{\"property\": \"propertyIRI\", \"value\": \"dbpedia\", \"operator\": \"contains\"}";
    params.setQuery(query);
    body.setSearchParams(params);
    body.setScopes(scopes);
    response = referenceSearcher.search(body);
    assertEquals(true, response.getResults().length > 0);
    //assertEquals(annoReference.getId(), response.getResultSet()[0].getReferenceId());
  }

  @Test
  @Transactional
  public void findByValueIRIContains() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    SearchScope[] scopes = { scope };
    query = "{\"property\": \"valueIRI\", \"value\": \"Almond\", \"operator\": \"contains\"}";
    params.setQuery(query);
    body.setSearchParams(params);
    body.setScopes(scopes);
    response = referenceSearcher.search(body);
    assertEquals(true, response.getResults().length > 0);
    //assertEquals(annoReference.getId(), response.getResultSet()[0].getReferenceId());
  }

  @Test
  @Transactional
  public void findExactlyOneReferenceByeAnnotationRegMatch() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    SearchScope[] scopes = { scope };
    query = "{\"property\": \"hasAnnotation\", \"value\": \"ingre.*::Almon.*\", \"operator\": \"regmatch\"}";
    body.setScopes(scopes);
    params.setQuery(query);
    body.setSearchParams(params);
    ResponseBody response = referenceSearcher.search(body);
    assertEquals(1, response.getResults().length);
    assertEquals(annoReference.getId(), response.getResultSet()[0].getReferenceId());
  }

  @Test
  @Transactional
  public void provokeExceptionByInvalidAnnotationIRI() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    TraversalRules[] rules = {};
    scope.setTraversalRules(rules);
    SearchScope[] scopes = { scope };
    String wrongAnnotation = "http://dbpedia.org/ontology/ingredienthttp://dbpedia.org/resource/Almond_milk";
    query = "{\"property\": \"hasAnnotationIRI\", \"value\": \"" + wrongAnnotation + "\", \"operator\": \"eq\"}";
    body.setScopes(scopes);
    SearchParams params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    assertThrows(ShepardParserException.class, () -> referenceSearcher.search(body));
  }

  @Test
  @Transactional
  public void provokeExceptionByTooManyColons() {
    scope.setCollectionId(collection1.getShepardId());
    scope.setDataObjectId(dataObjectc1d1.getShepardId());
    TraversalRules[] rules = {};
    scope.setTraversalRules(rules);
    SearchScope[] scopes = { scope };
    String wrongAnnotation = "A::B::C";
    query = "{\"property\": \"hasAnnotationIRI\", \"value\": \"" + wrongAnnotation + "\", \"operator\": \"eq\"}";
    body.setScopes(scopes);
    SearchParams params = new SearchParams();
    params.setQuery(query);
    body.setSearchParams(params);
    assertThrows(ShepardParserException.class, () -> referenceSearcher.search(body));
  }
}
