package de.dlr.shepard.search.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.util.TraversalRules;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class StructuredDataSearcherTest extends BaseTestCase {

  @InjectMock
  StructuredDataReferenceDAO structuredDataReferenceDAO;

  @InjectMock
  BasicReferenceDAO basicReferenceDAO;

  @InjectMock
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @InjectMock
  MongoCollection<Document> mongoContainer;

  @InjectMock
  FindIterable<Document> mongoQueryResult;

  @InjectMock
  Document firstDocument;

  @Inject
  StructuredDataSearcher structuredDataSearcher;

  @Test
  public void getStructuredDataResponseTest() {
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    String mongoID = "61371f2889b108615688e22e";
    // create StructuredDataReferences
    DataObject dataObject = new DataObject(dataObjectId);
    dataObject.setShepardId(dataObject.getId());
    List<StructuredData> structuredDatas = List.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
    StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
    sdContainer.setMongoId(mongoID);
    StructuredDataReference sdReference = new StructuredDataReference() {
      {
        setId(3L);
        setShepardId(3L);
        setDeleted(false);
        setName("reference1");
        setStructuredDatas(structuredDatas);
        setStructuredDataContainer(sdContainer);
        setDataObject(dataObject);
      }
    };
    // create SearchBody
    TraversalRules[] traversalRules = {};
    SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
    SearchScope scopes[] = { scope };
    String query = "xwert: {$gt: 0}";
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
    ResultTriple[] resultTriples = { resultTriple };
    BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionId, dataObjectId, "user1")).thenReturn(
      List.of(sdReference)
    );
    when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
    when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
    when(mongoQueryResult.first()).thenReturn(firstDocument);
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }

  @Test
  public void getStructuredDataResponseTest_TraversalRules() {
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    String mongoID = "61371f2889b108615688e22e";
    // create StructuredDataReferences
    DataObject dataObject = new DataObject(dataObjectId);
    dataObject.setShepardId(dataObject.getId());
    List<StructuredData> structuredDatas = List.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
    StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
    sdContainer.setMongoId(mongoID);
    StructuredDataReference sdReference = new StructuredDataReference() {
      {
        setId(3L);
        setShepardId(3L);
        setDeleted(false);
        setName("reference1");
        setStructuredDatas(structuredDatas);
        setStructuredDataContainer(sdContainer);
        setDataObject(dataObject);
      }
    };
    // create SearchBody
    TraversalRules[] traversalRules = { TraversalRules.children };
    SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
    SearchScope scopes[] = { scope };
    String query = "xwert: {$gt: 0}";
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
    ResultTriple[] resultTriples = { resultTriple };
    BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(
      structuredDataReferenceDAO.findReachableReferencesByShepardId(
        TraversalRules.children,
        collectionId,
        dataObjectId,
        "user1"
      )
    ).thenReturn(List.of(sdReference));
    when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
    when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
    when(mongoQueryResult.first()).thenReturn(firstDocument);
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }

  @Test
  public void getStructuredDataResponseTest_JsonQuery() {
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    String mongoID = "61371f2889b108615688e22e";
    // create StructuredDataReferences
    DataObject dataObject = new DataObject(dataObjectId);
    dataObject.setShepardId(dataObject.getId());
    List<StructuredData> structuredDatas = List.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
    StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
    sdContainer.setMongoId(mongoID);
    StructuredDataReference sdReference = new StructuredDataReference() {
      {
        setId(3L);
        setShepardId(3L);
        setDeleted(false);
        setName("reference1");
        setStructuredDatas(structuredDatas);
        setStructuredDataContainer(sdContainer);
        setDataObject(dataObject);
      }
    };
    // create SearchBody
    TraversalRules[] traversalRules = {};
    SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
    SearchScope scopes[] = { scope };
    String query =
      """
      {
        "property": "name",
        "value": "MyName",
        "operator": "eq"
      }
      """;
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
    ResultTriple[] resultTriples = { resultTriple };
    BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionId, dataObjectId, "user1")).thenReturn(
      List.of(sdReference)
    );
    when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
    when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
    when(mongoQueryResult.first()).thenReturn(firstDocument);
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }

  @Test
  public void getStructuredDataResponseTest_NoReferences() {
    // create SearchBody
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    TraversalRules[] traversalRules = {};
    SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
    SearchScope scopes[] = { scope };
    String query = "xwert: {$gt: 0}";
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple[] resultTriples = {};
    BasicEntityIO[] results = {};
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionId, dataObjectId, "user1")).thenReturn(
      Collections.emptyList()
    );
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }

  @Test
  public void getStructuredDataResponseTest_NoMatches() {
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    String mongoID = "61371f2889b108615688e22e";
    // create StructuredDataReferences
    DataObject dataObject = new DataObject(dataObjectId);
    dataObject.setShepardId(dataObject.getId());
    List<StructuredData> structuredDatas = List.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
    StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
    sdContainer.setMongoId(mongoID);
    StructuredDataReference sdReference = new StructuredDataReference() {
      {
        setId(3L);
        setShepardId(3L);
        setDeleted(false);
        setName("reference1");
        setStructuredDatas(structuredDatas);
        setStructuredDataContainer(sdContainer);
        setDataObject(dataObject);
      }
    };
    // create SearchBody
    TraversalRules[] traversalRules = {};
    SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
    SearchScope scopes[] = { scope };
    String query = "xwert: {$gt: 0}";
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple[] resultTriples = {};
    BasicEntityIO[] results = {};
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionId, dataObjectId, "user1")).thenReturn(
      List.of(sdReference)
    );
    when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
    when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
    when(mongoQueryResult.first()).thenReturn(null);
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }

  @Test
  public void throwsExeption() {
    SearchScope[] scope = { new SearchScope(null, 1L, null) };
    SearchBody searchBody = new SearchBody(scope, null);
    assertThrows(InvalidBodyException.class, () -> structuredDataSearcher.search(searchBody, "user1"));
  }

  @Test
  public void getStructuredDataResponseTestWithoutCollectionId() {
    Long collectionId = 1L;
    Long dataObjectId = 2L;
    String mongoID = "61371f2889b108615688e22e";
    // create StructuredDataReferences
    DataObject dataObject = new DataObject(dataObjectId);
    dataObject.setShepardId(dataObject.getId());
    List<StructuredData> structuredDatas = List.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
    StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
    sdContainer.setMongoId(mongoID);
    StructuredDataReference sdReference = new StructuredDataReference() {
      {
        setId(3L);
        setShepardId(3L);
        setDeleted(false);
        setName("reference1");
        setStructuredDatas(structuredDatas);
        setStructuredDataContainer(sdContainer);
        setDataObject(dataObject);
      }
    };
    // create SearchBody
    SearchScope scope = new SearchScope(collectionId, null, null);
    SearchScope scopes[] = { scope };
    String query = "xwert: {$gt: 0}";
    QueryType queryType = QueryType.StructuredData;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    // create ResponseBody
    ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
    ResultTriple[] resultTriples = { resultTriple };
    BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    // configure Mocks
    when(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionId, "user1")).thenReturn(
      List.of(sdReference)
    );
    when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
    when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
    when(mongoQueryResult.first()).thenReturn(firstDocument);
    // test
    var actual = structuredDataSearcher.search(searchBody, "user1");
    assertEquals(responseBody, actual);
  }
}
