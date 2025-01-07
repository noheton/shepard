package de.dlr.shepard.common.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class SearchDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private SearchDAO dao;

  @Test
  public void findCollectionsTest() {
    var collections = List.of(new Collection(1L));
    String query = "Match bla WITH col MATCH path=(col)-[]->(u:User) RETURN col, nodes(path), relationships(path)";
    when(session.query(Collection.class, query, Collections.emptyMap())).thenReturn(collections);
    var actual = dao.findCollections("Match bla", "col");
    assertEquals(collections, actual);
  }

  @Test
  public void findDataObjectsTest() {
    var dataObjects = List.of(new DataObject(1L));
    String query =
      "Match bla WITH do MATCH path=(c:Collection)-[]->(do)-[]->(u:User) RETURN do, nodes(path), relationships(path)";
    when(session.query(DataObject.class, query, Collections.emptyMap())).thenReturn(dataObjects);
    var actual = dao.findDataObjects("Match bla", "do");
    assertEquals(dataObjects, actual);
  }

  @Test
  public void findReferencesTest() {
    var references = List.of(new BasicReference(1L));
    String query =
      "Match bla WITH ref MATCH path=(c:Collection)-[]->(d:DataObject)-[]->(ref)-[]->(u:User) RETURN ref, nodes(path), relationships(path)";
    when(session.query(BasicReference.class, query, Collections.emptyMap())).thenReturn(references);
    var actual = dao.findReferences("Match bla", "ref");
    assertEquals(references, actual);
  }

  @Test
  public void findFileContainersTest() {
    var fileContainers = List.of(new FileContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH fc MATCH path=(fc)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN fc, nodes(path), relationships(path)";
    when(session.query(FileContainer.class, query, Collections.emptyMap())).thenReturn(fileContainers);
    var actual = dao.findFileContainers(selectionQuery, "fc");
    assertEquals(fileContainers, actual);
  }

  @Test
  public void findStructuredDataContainersTest() {
    var structuredDataContainers = List.of(new StructuredDataContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH sd MATCH path=(sd)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN sd, nodes(path), relationships(path)";
    when(session.query(StructuredDataContainer.class, query, Collections.emptyMap())).thenReturn(
      structuredDataContainers
    );
    var actual = dao.findStructuredDataContainers(selectionQuery, "sd");
    assertEquals(structuredDataContainers, actual);
  }

  @Test
  public void findTimeseriesContainersTest() {
    var timeseriesContainers = List.of(new TimeseriesContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH ts MATCH path=(ts)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN ts, nodes(path), relationships(path)";
    when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(timeseriesContainers);
    var actual = dao.findTimeseriesContainers(selectionQuery, "ts");
    assertEquals(timeseriesContainers, actual);
  }

  @Test
  public void findUsersTest() {
    var users = List.of(new User("user"));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH user MATCH path=(user:User)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN user, nodes(path), relationships(path)";
    when(session.query(User.class, query, Collections.emptyMap())).thenReturn(users);
    var actual = dao.findUsers(selectionQuery, "user");
    assertEquals(users, actual);
  }
}
