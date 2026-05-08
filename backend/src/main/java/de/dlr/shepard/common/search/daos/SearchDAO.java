package de.dlr.shepard.common.search.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.neo4j.ogm.session.Session;

@RequestScoped
public class SearchDAO {

  protected Session session = null;

  public SearchDAO() {
    session = NeoConnector.getInstance().getNeo4jSession();
  }

  public List<Collection> findCollections(
    Neo4jQuery selectionQuery,
    PaginationHelper pagination,
    String collectionVariable
  ) {
    String query = selectionQuery.cypher() + emitCollectionReturnPart(collectionVariable, pagination);
    Iterable<Collection> collections = session.query(Collection.class, query, selectionQuery.params());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public Integer getCollectionTotalCount(Neo4jQuery selectionQuery, String collectionVariable) {
    String query = "%s RETURN COUNT(%s)".formatted(selectionQuery.cypher(), collectionVariable);
    Iterable<Integer> collectionTotalCountIterable = session.query(Integer.class, query, selectionQuery.params());
    return collectionTotalCountIterable.iterator().next();
  }

  public List<DataObject> findDataObjects(Neo4jQuery selectionQuery, String dataObjectVariable) {
    String query = selectionQuery.cypher() + emitDataObjectReturnPart(dataObjectVariable);
    Iterable<DataObject> collections = session.query(DataObject.class, query, selectionQuery.params());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public List<BasicReference> findReferences(Neo4jQuery selectionQuery, String referenceVariable) {
    String query = selectionQuery.cypher() + emitReferencesReturnPart(referenceVariable);
    Iterable<BasicReference> collections = session.query(BasicReference.class, query, selectionQuery.params());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public List<BasicContainer> findContainers(
    Neo4jQuery selectionQuery,
    PaginationHelper pagination,
    String containerVariable
  ) {
    String query = selectionQuery.cypher() + emitContainerReturnPart(containerVariable, pagination);
    Iterable<BasicContainer> basicContainers = session.query(BasicContainer.class, query, selectionQuery.params());
    List<BasicContainer> ret = new ArrayList<>();
    basicContainers.forEach(ret::add);
    return ret;
  }

  public Integer getContainerTotalCount(Neo4jQuery selectionQuery, String containerVariable) {
    String query = selectionQuery.cypher() + emitTotalCountReturnPart(containerVariable);
    Iterable<Integer> containerTotalCountIterable = session.query(Integer.class, query, selectionQuery.params());
    return containerTotalCountIterable.iterator().next();
  }

  public List<User> findUsers(Neo4jQuery selectionQuery, String userVariable) {
    String query = selectionQuery.cypher() + emitUserReturnPart(userVariable);
    Iterable<User> users = session.query(User.class, query, selectionQuery.params());
    List<User> ret = new ArrayList<>();
    users.forEach(ret::add);
    return ret;
  }

  public List<UserGroup> findUserGroups(Neo4jQuery selectionQuery, String userGroupVariable) {
    String query = selectionQuery.cypher() + emitUserGroupReturnPart(userGroupVariable);
    Iterable<UserGroup> userGroups = session.query(UserGroup.class, query, selectionQuery.params());
    List<UserGroup> ret = new ArrayList<>();
    userGroups.forEach(ret::add);
    return ret;
  }

  private String emitTotalCountReturnPart(String containerVariable) {
    return (
      " WITH " +
      containerVariable +
      " " +
      CypherQueryHelper.getReturnCountPart(containerVariable, Neighborhood.ESSENTIAL)
    );
  }

  private String emitContainerReturnPart(String containerVariable, PaginationHelper pagination) {
    return (
      " WITH " +
      containerVariable +
      " " +
      CypherQueryHelper.getReturnPart(containerVariable, Neighborhood.ESSENTIAL, pagination)
    );
  }

  private String emitCollectionReturnPart(String collectionVariable, PaginationHelper pagination) {
    return (
      (pagination != null ? " " + CypherQueryHelper.getPaginationPart(pagination) : "") +
      " WITH " +
      collectionVariable +
      " " +
      CypherQueryHelper.getReturnPart(collectionVariable, Neighborhood.ESSENTIAL)
    );
  }

  private String emitDataObjectReturnPart(String dataObjectVariable) {
    return " WITH %s MATCH path=(c:Collection)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)".formatted(
        dataObjectVariable,
        dataObjectVariable,
        dataObjectVariable
      );
  }

  private String emitReferencesReturnPart(String referenceVariable) {
    return " WITH %s MATCH path=(c:Collection)-[]->(d:DataObject)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)".formatted(
        referenceVariable,
        referenceVariable,
        referenceVariable
      );
  }

  private String emitUserReturnPart(String userVariable) {
    return " WITH %s MATCH path=(%s:User)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN %s, nodes(path), relationships(path)".formatted(
        userVariable,
        userVariable,
        userVariable
      );
  }

  private String emitUserGroupReturnPart(String userGroupVariable) {
    return " WITH %s MATCH path=(%s:UserGroup)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN %s, nodes(path), relationships(path)".formatted(
        userGroupVariable,
        userGroupVariable,
        userGroupVariable
      );
  }
}
