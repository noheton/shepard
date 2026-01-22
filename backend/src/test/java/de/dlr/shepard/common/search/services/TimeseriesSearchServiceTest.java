package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.io.AnnotatableTimeseriesInContainerSearchParams;
import de.dlr.shepard.common.search.io.TimeseriesInContainerSearchBody;
import de.dlr.shepard.common.search.io.TimeseriesInContainerSearchResult;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticRepositoryService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class TimeseriesSearchServiceTest {

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
  TimeseriesSearchService timeseriesSearchService;

  @Inject
  AnnotatableTimeseriesService annotatableTimeseriesService;

  @Inject
  AnnotatableTimeseriesDAO annotatableTimeseriesDAO;

  @Inject
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesContainerDAO timeseriesContainerDAO;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserService userService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  private Collection collection1;
  private DataObject dataObjectc1d1;
  private DataObject dataObjectc1d2;
  private TimeseriesReference tsref1;
  private TimeseriesReference tsref2;
  private TimeseriesReference tsref1a;
  private TimeseriesContainer tsCon1;
  private TimeseriesContainer tsCon2;
  private TimeseriesEntity timeseriesEntity1;
  private TimeseriesEntity timeseriesEntity1a;
  private TimeseriesEntity timeseriesEntity2;
  private Timeseries ts1;
  private Timeseries ts1a;
  private Timeseries ts2;
  private SemanticAnnotation ts1Anno;
  private SemanticAnnotation ts1aAnno;
  private SemanticAnnotation ts2Anno;
  private SemanticRepository repository;
  private User user;
  private User user1;
  private String username = "u";
  private String username1 = "u1";
  private TimeseriesInContainerSearchBody searchBody;
  private AnnotatableTimeseriesInContainerSearchParams searchParams;
  private String query;
  private TimeseriesInContainerSearchResult searchResult;

  @BeforeEach
  public void setUp() {
    if (user == null) {
      user = new User(username);
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(username, "key"));
    }
    //create user1
    if (user1 == null) {
      user1 = new User(username1);
      userService.createOrUpdateUser(user1);
    }
    if (tsCon1 == null) {
      //create containers
      TimeseriesContainerIO tsConIO = new TimeseriesContainerIO();
      tsConIO.setName("container");
      tsCon1 = timeseriesContainerService.createContainer(tsConIO);
      TimeseriesContainerIO tsCon1IO = new TimeseriesContainerIO();
      tsCon1IO.setName("container1");
      tsCon2 = timeseriesContainerService.createContainer(tsCon1IO);
      //create timeseries
      TimeseriesDataPoint dataPoint1 = new TimeseriesDataPoint(1l, true);
      TimeseriesDataPoint dataPoint2 = new TimeseriesDataPoint(2l, true);
      TimeseriesDataPoint dataPoint3 = new TimeseriesDataPoint(3l, true);
      List<TimeseriesDataPoint> points1 = new ArrayList<TimeseriesDataPoint>();
      points1.add(dataPoint1);
      List<TimeseriesDataPoint> points2 = new ArrayList<TimeseriesDataPoint>();
      points2.add(dataPoint2);
      List<TimeseriesDataPoint> points3 = new ArrayList<TimeseriesDataPoint>();
      points3.add(dataPoint3);
      ts1 = new Timeseries("m1", "d1", "l1", "s1", "f1");
      ts1a = new Timeseries("m1a", "d1a", "l1a", "s1a", "f1a");
      ts2 = new Timeseries("m2", "d2", "l2", "s2", "f2");
      timeseriesEntity1 = timeseriesService.saveDataPoints(tsCon1.getId(), ts1, points1);
      timeseriesEntity2 = timeseriesService.saveDataPoints(tsCon2.getId(), ts2, points2);
      timeseriesEntity1a = timeseriesService.saveDataPoints(tsCon1.getId(), ts1a, points3);
    }
    //create SemanticRepository
    SemanticRepositoryIO repToCreate = new SemanticRepositoryIO();
    repToCreate.setName("SemanticRepository");
    repToCreate.setType(SemanticRepositoryType.SPARQL);
    repToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    repository = semanticRepositoryService.createRepository(repToCreate);
    //annotate ts1
    SemanticAnnotationIO AnnoToCreate = new SemanticAnnotationIO();
    AnnoToCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    AnnoToCreate.setPropertyRepositoryId(repository.getId());
    AnnoToCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    AnnoToCreate.setValueRepositoryId(repository.getId());
    ts1Anno = annotatableTimeseriesService.createAnnotation(tsCon1.getId(), timeseriesEntity1.getId(), AnnoToCreate);
    //annotate ts1a
    SemanticAnnotationIO AnnoToCreate1a = new SemanticAnnotationIO();
    AnnoToCreate1a.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    AnnoToCreate1a.setPropertyRepositoryId(repository.getId());
    AnnoToCreate1a.setValueIRI("http://dbpedia.org/resource/Oat_milk");
    AnnoToCreate1a.setValueRepositoryId(repository.getId());
    ts1aAnno = annotatableTimeseriesService.createAnnotation(
      tsCon1.getId(),
      timeseriesEntity1a.getId(),
      AnnoToCreate1a
    );
    //annotate ts2
    SemanticAnnotationIO AnnoToCreate2 = new SemanticAnnotationIO();
    AnnoToCreate2.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    AnnoToCreate2.setPropertyRepositoryId(repository.getId());
    AnnoToCreate2.setValueIRI("http://dbpedia.org/resource/Rice_milk");
    AnnoToCreate2.setValueRepositoryId(repository.getId());
    ts2Anno = annotatableTimeseriesService.createAnnotation(tsCon2.getId(), timeseriesEntity2.getId(), AnnoToCreate2);
    searchBody = new TimeseriesInContainerSearchBody();
    searchParams = new AnnotatableTimeseriesInContainerSearchParams();
  }
  /*@Test
  @Transactional
  public void searchAnnotatedTimeseries() {
    //search in container2 for one existing annoTS
    searchParams.setContainerId(tsCon2.getId());
    query = "{\"property\": \"hasAnnotationIRI\", \"value\": \".*ingre.*::.*Rice.*\", \"operator\": \"regmatch\"}";
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    searchResult = timeseriesSearchService.search(tsCon2.getId(), searchBody);
    assertEquals(1, searchResult.getResults().size());
    //search in container1 for non-existing annoTS
    searchParams.setContainerId(tsCon1.getId());
    query = "{\"property\": \"hasAnnotationIRI\", \"value\": \".*ingre.*::.*Rice.*\", \"operator\": \"regmatch\"}";
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    searchResult = timeseriesSearchService.search(tsCon1.getId(), searchBody);
    assertEquals(0, searchResult.getResults().size());
    //search in container1 for two existing annoTS
    searchParams.setContainerId(tsCon1.getId());
    query = "{\"property\": \"hasAnnotationIRI\", \"value\": \".*ingre.*::.*milk.*\", \"operator\": \"regmatch\"}";
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    searchResult = timeseriesSearchService.search(tsCon1.getId(), searchBody);
    assertEquals(2, searchResult.getResults().size());
    System.out.println(searchResult.getResults().get(0).toString());
  }*/
}
