package de.dlr.shepard.common.search.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Arrays;
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
    String query =
      "Match bla WITH col MATCH path=(col)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN col, nodes(path), relationships(path)";
    when(session.query(Collection.class, query, Collections.emptyMap())).thenReturn(collections);
    var actual = dao.findCollections("Match bla", null, "col");
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
  public void getContainerTotalCountTest() {
    int containerCount = 1;
    Iterable<Integer> countIterable = () -> Arrays.stream(new Integer[] { containerCount }).iterator();
    String selectionQuery = "MATCH bla";
    String query = "MATCH bla WITH bc MATCH path=(bc)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN COUNT(bc)";
    when(session.query(Integer.class, query, Collections.emptyMap())).thenReturn(countIterable);
    var actual = dao.getContainerTotalCount(selectionQuery, "bc");
    assertEquals(containerCount, actual);
  }

  @Test
  public void findBasicContainersTest() {
    List<BasicContainer> basicContainers = List.of(new BasicContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH bc MATCH path=(bc)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN bc, nodes(path), relationships(path)";
    when(session.query(BasicContainer.class, query, Collections.emptyMap())).thenReturn(basicContainers);
    var actual = dao.findContainers(selectionQuery, null, "bc");
    assertEquals(basicContainers, actual);
  }

  @Test
  public void findFileContainersTest() {
    List<BasicContainer> fileContainers = List.of(new FileContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH fc MATCH path=(fc)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN fc, nodes(path), relationships(path)";
    when(session.query(BasicContainer.class, query, Collections.emptyMap())).thenReturn(fileContainers);
    var actual = dao.findContainers(selectionQuery, null, "fc");
    assertEquals(fileContainers, actual);
  }

  @Test
  public void findStructuredDataContainersTest() {
    List<BasicContainer> structuredDataContainers = List.of(new StructuredDataContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH sd MATCH path=(sd)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN sd, nodes(path), relationships(path)";
    when(session.query(BasicContainer.class, query, Collections.emptyMap())).thenReturn(structuredDataContainers);
    var actual = dao.findContainers(selectionQuery, null, "sd");
    assertEquals(structuredDataContainers, actual);
  }

  @Test
  public void findTimeseriesContainersTest() {
    List<BasicContainer> timeseriesContainers = List.of(new TimeseriesContainer(1L));
    String selectionQuery = "MATCH bla";
    String query =
      "MATCH bla WITH ts MATCH path=(ts)-[*0..1]->(n) WHERE n:Permission OR n:User RETURN ts, nodes(path), relationships(path)";
    when(session.query(BasicContainer.class, query, Collections.emptyMap())).thenReturn(timeseriesContainers);
    var actual = dao.findContainers(selectionQuery, null, "ts");
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
