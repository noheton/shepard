package de.dlr.shepard.common.search.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
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
    String selectionQuery,
    PaginationHelper pagination,
    String collectionVariable
  ) {
    String query = selectionQuery + emitCollectionReturnPart(collectionVariable, pagination);
    System.out.println("overall query: " + query);
    Iterable<Collection> collections = session.query(Collection.class, query, Collections.emptyMap());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public List<Collection> findCollections(String query) {
    Iterable<Collection> collections = session.query(Collection.class, query, Collections.emptyMap());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public Integer getCollectionTotalCount(String selectionQuery, String collectionVariable) {
    String query = String.format("%s RETURN COUNT(%s)", selectionQuery, collectionVariable);
    Iterable<Integer> collectionTotalCountIterable = session.query(Integer.class, query, Collections.emptyMap());
    return collectionTotalCountIterable.iterator().next();
  }

  public List<DataObject> findDataObjects(String selectionQuery, String dataObjectVariable) {
    String query = selectionQuery + emitDataObjectReturnPart(dataObjectVariable);
    Iterable<DataObject> collections = session.query(DataObject.class, query, Collections.emptyMap());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public List<BasicReference> findReferences(String selectionQuery, String referenceVariable) {
    String query = selectionQuery + emitReferencesReturnPart(referenceVariable);
    Iterable<BasicReference> collections = session.query(BasicReference.class, query, Collections.emptyMap());
    var ret = StreamSupport.stream(collections.spliterator(), false).toList();
    return ret;
  }

  public List<BasicContainer> findContainers(
    String selectionQuery,
    PaginationHelper pagination,
    String containerVariable
  ) {
    String query = selectionQuery + emitContainerReturnPart(containerVariable, pagination);
    Iterable<BasicContainer> basicContainers = session.query(BasicContainer.class, query, Collections.emptyMap());
    List<BasicContainer> ret = new ArrayList<>();
    basicContainers.forEach(ret::add);
    return ret;
  }

  public Integer getContainerTotalCount(String selectionQuery, String containerVariable) {
    String query = selectionQuery + emitTotalCountReturnPart(containerVariable);
    Iterable<Integer> containerTotalCountIterable = session.query(Integer.class, query, Collections.emptyMap());
    return containerTotalCountIterable.iterator().next();
  }

  public List<User> findUsers(String selectionQuery, String userVariable) {
    String query = selectionQuery + emitUserReturnPart(userVariable);
    Iterable<User> users = session.query(User.class, query, Collections.emptyMap());
    List<User> ret = new ArrayList<>();
    users.forEach(ret::add);
    return ret;
  }

  public List<UserGroup> findUserGroups(String selectionQuery, String userGroupVariable) {
    String query = selectionQuery + emitUserGroupReturnPart(userGroupVariable);
    Iterable<UserGroup> userGroups = session.query(UserGroup.class, query, Collections.emptyMap());
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
    return String.format(
      " WITH %s MATCH path=(c:Collection)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)",
      dataObjectVariable,
      dataObjectVariable,
      dataObjectVariable
    );
  }

  private String emitReferencesReturnPart(String referenceVariable) {
    return String.format(
      " WITH %s MATCH path=(c:Collection)-[]->(d:DataObject)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)",
      referenceVariable,
      referenceVariable,
      referenceVariable
    );
  }

  private String emitUserReturnPart(String userVariable) {
    return String.format(
      " WITH %s MATCH path=(%s:User)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN %s, nodes(path), relationships(path)",
      userVariable,
      userVariable,
      userVariable
    );
  }

  private String emitUserGroupReturnPart(String userGroupVariable) {
    return String.format(
      " WITH %s MATCH path=(%s:UserGroup)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN %s, nodes(path), relationships(path)",
      userGroupVariable,
      userGroupVariable,
      userGroupVariable
    );
  }
}
