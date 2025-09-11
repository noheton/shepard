package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
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
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class TimeseriesServicePlaygroundQuarkusTest {

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
  AnnotatableTimeseriesSearchService annotatableTimeseriesSearchService;

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
  private TimeseriesContainer tsCon;
  private TimeseriesContainer tsCon1;
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
  private boolean cleanUp = true;

  @BeforeEach
  public void setUp() {
    if (cleanUp) annotatableTimeseriesDAO.runQuery("MATCH (n) DETACH DELETE n", Collections.emptyMap());
    //create user
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
    //create collection
    if (collection1 == null) {
      CollectionIO collection1IO = new CollectionIO();
      collection1IO.setName("collection1" + System.currentTimeMillis());
      collection1 = collectionService.createCollection(collection1IO);
    }
    //create dataobject1
    if (dataObjectc1d1 == null) {
      DataObjectIO dataObjectc1d1IO = new DataObjectIO();
      dataObjectc1d1IO.setName("c1do1" + System.currentTimeMillis());
      dataObjectc1d1 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d1IO);
    }
    //create dataobject2
    if (dataObjectc1d2 == null) {
      DataObjectIO dataObjectc1d2IO = new DataObjectIO();
      dataObjectc1d2IO.setName("c1do2" + System.currentTimeMillis());
      dataObjectc1d2 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectc1d2IO);
    }
    //create timeseriesContainers with content
    if (tsCon == null) {
      //create containers
      TimeseriesContainerIO tsConIO = new TimeseriesContainerIO();
      tsConIO.setName("container");
      tsCon = timeseriesContainerService.createContainer(tsConIO);
      TimeseriesContainerIO tsCon1IO = new TimeseriesContainerIO();
      tsCon1IO.setName("container1");
      tsCon1 = timeseriesContainerService.createContainer(tsCon1IO);
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
      ts1a = new Timeseries("m1", "d1", "l1", "s1", "f1");
      ts2 = new Timeseries("m2", "d2", "l2", "s2", "f2");
      timeseriesEntity1 = timeseriesService.saveDataPoints(tsCon.getId(), ts1, points1);
      timeseriesEntity2 = timeseriesService.saveDataPoints(tsCon1.getId(), ts2, points2);
      timeseriesEntity1a = timeseriesService.saveDataPoints(tsCon1.getId(), ts1a, points3);
      System.out.println(
        "tsE1.id: " +
        timeseriesEntity1.getId() +
        ", tsE1.dev: " +
        timeseriesEntity1.getDevice() +
        ", tsE1.con: " +
        timeseriesEntity1.getContainerId()
      );
      System.out.println(
        "tsE1a.id: " +
        timeseriesEntity1a.getId() +
        ", tsE1a.dev: " +
        timeseriesEntity1a.getDevice() +
        ", tsE1a.con: " +
        timeseriesEntity1a.getContainerId()
      );
      System.out.println(
        "tsE2.id: " +
        timeseriesEntity2.getId() +
        ", tsE2.dev: " +
        timeseriesEntity2.getDevice() +
        ", tsE2.con: " +
        timeseriesEntity2.getContainerId()
      );
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
      ts1Anno = annotatableTimeseriesService.createAnnotation(tsCon.getId(), timeseriesEntity1.getId(), AnnoToCreate);
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
      ts2Anno = annotatableTimeseriesService.createAnnotation(tsCon1.getId(), timeseriesEntity2.getId(), AnnoToCreate2);
      //create Reference on ts1
      TimeseriesReferenceIO ref1IO = new TimeseriesReferenceIO();
      ref1IO.setName("ref1");
      ref1IO.setStart(0l);
      ref1IO.setEnd(9l);
      List<Timeseries> timeseries1 = new ArrayList<Timeseries>();
      timeseries1.add(ts1);
      ref1IO.setTimeseries(timeseries1);
      ref1IO.setTimeseriesContainerId(tsCon.getId());
      tsref1 = timeseriesReferenceService.createReference(
        collection1.getShepardId(),
        dataObjectc1d1.getShepardId(),
        ref1IO
      );
      //create Reference on ts2
      TimeseriesReferenceIO ref2IO = new TimeseriesReferenceIO();
      ref2IO.setName("ref2");
      ref2IO.setStart(0l);
      ref2IO.setEnd(9l);
      List<Timeseries> timeseries2 = new ArrayList<Timeseries>();
      timeseries2.add(ts2);
      ref2IO.setTimeseries(timeseries2);
      ref2IO.setTimeseriesContainerId(tsCon1.getId());
      tsref2 = timeseriesReferenceService.createReference(
        collection1.getShepardId(),
        dataObjectc1d1.getShepardId(),
        ref2IO
      );
      //create Reference on ts1a
      TimeseriesReferenceIO ref1aIO = new TimeseriesReferenceIO();
      ref1aIO.setName("ref1a");
      ref1aIO.setStart(0l);
      ref1aIO.setEnd(9l);
      List<Timeseries> timeseries1a = new ArrayList<Timeseries>();
      timeseries1a.add(ts1a);
      ref1aIO.setTimeseries(timeseries1a);
      ref1aIO.setTimeseriesContainerId(tsCon1.getId());
      tsref1a = timeseriesReferenceService.createReference(
        collection1.getShepardId(),
        dataObjectc1d2.getShepardId(),
        ref1aIO
      );
      System.out.println(
        "tsref1Container: " +
        tsref1.getTimeseriesContainer().getId() +
        ", tsref1.device: " +
        tsref1.getReferencedTimeseriesList().get(0).getDevice()
      );
      System.out.println(
        "tsref1aContainer: " +
        tsref1a.getTimeseriesContainer().getId() +
        ", tsref1a.device: " +
        tsref1a.getReferencedTimeseriesList().get(0).getDevice()
      );
      System.out.println(
        "tsref2Container: " +
        tsref2.getTimeseriesContainer().getId() +
        ", tsref2.device: " +
        tsref2.getReferencedTimeseriesList().get(0).getDevice()
      );
    }
  }

  /*@Test
  @Transactional
  public void Test() {
    System.out.println(username);
    System.out.println("container: " + timeseriesService.getTimeseriesAvailable(tsCon.getId()).get(0).getId());
    System.out.println("container1: " + timeseriesService.getTimeseriesAvailable(tsCon1.getId()).get(0).getId());
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(0, 10, null, null, null);
    System.out.println(timeseriesService.getDataPointsByTimeseries(tsCon.getId(), ts1, queryParams));
    System.out.println(timeseriesService.getDataPointsByTimeseries(tsCon1.getId(), ts1, queryParams));
    String query = "MATCH(n) RETURN n";
    annotableTimeseriesDAO.runQuery(query, Collections.emptyMap());
  }*/

  @Test
  @Transactional
  public void referenceAnnotatedTimeseries() {
    authenticationContext.setPrincipal(new JWTPrincipal(username1, "key"));
    List<TimeseriesContainer> allContainers = timeseriesContainerService.getContainers();
    for (TimeseriesContainer container : allContainers) {
      List<TimeseriesEntity> timeseriesEntitiesInContainer = timeseriesRepository.list(
        "containerId",
        container.getId()
      );
      for (TimeseriesEntity tsEntityInContainer : timeseriesEntitiesInContainer) {
        System.out.println(
          "container: " +
          container.getId() +
          ", id: " +
          tsEntityInContainer.getId() +
          ", device: " +
          tsEntityInContainer.getDevice()
        );
        String query =
          "MATCH (ats:AnnotatableTimeseries), (ts:Timeseries)<-[:has_payload]-(tsr:TimeseriesReference)-[:is_in_container]->(tsc:TimeseriesContainer) ";
        query = query + "WHERE ";
        query = query + "ats.timeseriesId = " + tsEntityInContainer.getId();
        query = query + " AND ";
        query = query + "ats.containerId = id(tsc) ";
        query = query + " AND ";
        query = query + "ts.device = \"" + tsEntityInContainer.getDevice() + "\" ";
        query = query + " CREATE (ats)-[:corresponds_to]->(ts)";
        System.out.println("query: " + query);
        boolean success = annotatableTimeseriesDAO.runQuery(query, Collections.emptyMap());
        System.out.println("query successful: " + success);
      }
    }
  }
}
