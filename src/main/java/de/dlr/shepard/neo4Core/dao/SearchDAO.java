package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.CypherQueryHelper;

public class SearchDAO {

	protected Session session = null;

	public SearchDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	public List<Collection> findCollections(String selectionQuery, String collectionVariable) {
		String query = selectionQuery + emitCollectionReturnPart(collectionVariable);
		Iterable<Collection> collections = session.query(Collection.class, query, Collections.emptyMap());
		var ret = StreamSupport.stream(collections.spliterator(), false).toList();
		return ret;
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

	public List<FileContainer> findFileContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<FileContainer> fileContainers = session.query(FileContainer.class, query, Collections.emptyMap());
		List<FileContainer> ret = new ArrayList<>();
		fileContainers.forEach(ret::add);
		return ret;
	}

	public List<StructuredDataContainer> findStructuredDataContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<StructuredDataContainer> structuredDataContainers = session.query(StructuredDataContainer.class, query,
				Collections.emptyMap());
		List<StructuredDataContainer> ret = new ArrayList<>();
		structuredDataContainers.forEach(ret::add);
		return ret;
	}

	public List<TimeseriesContainer> findTimeseriesContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<TimeseriesContainer> timeseriesContainers = session.query(TimeseriesContainer.class, query,
				Collections.emptyMap());
		List<TimeseriesContainer> ret = new ArrayList<>();
		timeseriesContainers.forEach(ret::add);
		return ret;
	}

	public List<User> findUsers(String selectionQuery, String userVariable) {
		String query = selectionQuery + emitUserReturnPart(userVariable);
		Iterable<User> users = session.query(User.class, query, Collections.emptyMap());
		List<User> ret = new ArrayList<>();
		users.forEach(ret::add);
		return ret;
	}

	private String emitContainerReturnPart(String containerVariable) {
		return " WITH " + containerVariable + " " + CypherQueryHelper.getReturnPart(containerVariable, true);
	}

	private String emitCollectionReturnPart(String collectionVariable) {
		return String.format(" WITH %s MATCH path=(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)",
				collectionVariable, collectionVariable, collectionVariable);
	}

	private String emitDataObjectReturnPart(String dataObjectVariable) {
		return String.format(
				" WITH %s MATCH path=(c:Collection)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)",
				dataObjectVariable, dataObjectVariable, dataObjectVariable);
	}

	private String emitReferencesReturnPart(String referenceVariable) {
		return String.format(
				" WITH %s MATCH path=(c:Collection)-[]->(d:DataObject)-[]->(%s)-[]->(u:User) RETURN %s, nodes(path), relationships(path)",
				referenceVariable, referenceVariable, referenceVariable);
	}

	private String emitUserReturnPart(String userVariable) {
		return String.format(
				" WITH %s MATCH path=(%s:User)<-[:belongs_to|subscribed_by*0..1]-(n) RETURN %s, nodes(path), relationships(path)",
				userVariable, userVariable, userVariable);
	}

}
