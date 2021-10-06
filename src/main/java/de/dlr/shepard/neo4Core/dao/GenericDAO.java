package de.dlr.shepard.neo4Core.dao;

import java.util.Collection;
import java.util.Map;

import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.query.Pagination;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.PaginationHelper;
import de.dlr.shepard.util.TraversalRules;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class GenericDAO<T> {
	protected static final int DEPTH_ENTITY = 1;

	protected Session session = null;

	public GenericDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	/**
	 * Find all instances of a certain entity T
	 *
	 * @return an Iterable over the found entities
	 */
	public Collection<T> findAll() {
		Collection<T> iter = session.loadAll(getEntityType(), DEPTH_ENTITY);
		return iter;
	}

	/**
	 * Find all instances of a certain entity T
	 *
	 * @param page which page should be fetched
	 * @return an Iterable over the found entities
	 */
	public Collection<T> findAll(PaginationHelper page) {
		Collection<T> iter = session.loadAll(getEntityType(), new Pagination(page.getPage(), page.getSize()),
				DEPTH_ENTITY);
		return iter;
	}

	/**
	 * Find the entity with the given id
	 *
	 * @param id The given id
	 * @return The entity with the given id or null
	 */
	public T find(long id) {
		T object = session.load(getEntityType(), id, DEPTH_ENTITY);
		return object;
	}

	/**
	 * Find entities matching the given filter
	 *
	 * @param filter The given filter
	 * @return An iterable with the found entities
	 */
	public Collection<T> findMatching(Filter filter) {
		Collection<T> iter = session.loadAll(getEntityType(), filter, DEPTH_ENTITY);
		return iter;
	}

	/**
	 * Delete an entity
	 *
	 * @param id The entity to be deleted
	 * @return Whether the deletion was successful or not
	 */
	public boolean delete(long id) {
		T entity = session.load(getEntityType(), id);
		if (entity != null) {
			session.delete(entity);
			return true;
		}
		return false;
	}

	/**
	 * Save an entity and all related entities
	 *
	 * @param entity The entity to be saved
	 * @return the saved entity
	 */
	public T createOrUpdate(T entity) {
		session.save(entity, DEPTH_ENTITY);
		return entity;
	}

	/**
	 * CAUTION: The query runs against the database and is not checked. You can do
	 * anything you want.
	 *
	 * @param query     The query
	 * @param paramsMap Map of parameters
	 * @return Iterable The result
	 */
	protected Iterable<T> findByQuery(String query, Map<String, Object> paramsMap) {
		log.debug("Run query: {}", query);
		String params = "";
		for (String key : paramsMap.keySet()) {
			params += "(" + key + ", " + paramsMap.get(key) + "), ";
		}
		log.debug("queryParams: {}", params);
		Iterable<T> iter = session.query(getEntityType(), query, paramsMap);
		return iter;
	}

	protected boolean runQuery(String query, Map<String, Object> paramsMap) {
		log.debug("Run query: {}", query);
		Result result = session.query(query, paramsMap);
		return result.queryStatistics().containsUpdates();
	}

	protected String getObjectPart(String variable, String type, boolean hasName) {
		if (hasName)
			return getObjectPartWithName(variable, type);
		else
			return getObjectPartWithoutName(variable, type);
	}

	private String getObjectPartWithName(String variable, String type) {
		var namePart = String.format("{ name : $name, deleted: false }");
		var result = String.format("(%s:%s %s)", variable, type, namePart);
		return result;
	}

	private String getObjectPartWithoutName(String variable, String type) {
		var namePart = String.format("{ deleted: false }");
		var result = String.format("(%s:%s %s)", variable, type, namePart);
		return result;
	}

	protected String getPaginationPart() {
		return "SKIP $offset LIMIT $size";
	}

	protected String getReturnPart(String entity) {
		return getReturnPart(entity, false);
	}

	protected String getReturnPart(String entity, boolean omitIncoming) {
		var baseString = omitIncoming
				? "MATCH path=(%s)-[*0..1]->(n) WHERE n.deleted = false or n.deleted IS NULL RETURN %s, nodes(path), relationships(path)"
				: "MATCH path=(%s)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL RETURN %s, nodes(path), relationships(path)";
		var result = String.format(baseString, entity, entity);
		return result;
	}

	protected String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
		String ret;
		boolean isString = orderByAttribute.isString();
		if (!isString)
			ret = "ORDER BY " + variable + "." + orderByAttribute;
		else
			ret = "ORDER BY toLower(" + variable + "." + orderByAttribute + ")";
		if (orderDesc != null && orderDesc == true)
			ret = ret + " DESC";
		return ret;
	}

	public String getSearchForReachableReferencesQuery(TraversalRules traversalRule, long startId) {
		String ret = "";
		switch (traversalRule) {
		case children:
			ret = "MATCH path = (d:DataObject)-[hc:has_child*0..]->(e:DataObject)-[hr:has_reference]->";
			break;
		case parents:
			ret = "MATCH path = (d:DataObject)<-[hc:has_child*0..]-(e:DataObject)-[hr:has_reference]->";
			break;
		case successors:
			ret = "MATCH path = (d:DataObject)-[hc:has_successor*0..]->(e:DataObject)-[hr:has_reference]->";
			break;
		case predecessors:
			ret = "MATCH path = (d:DataObject)<-[hc:has_successor*0..]-(e:DataObject)-[hr:has_reference]->";
			break;
		}
		ret = ret + "(r:" + getEntityType().getSimpleName() + ")";
		ret = ret + " WITH nodes(path) as ns, r as ret WHERE id(d) = " + startId;
		ret = ret + " and NONE(node IN ns WHERE (node.deleted = TRUE)) ";
		String returnPart = getReturnPart("ret", false);
		ret = ret + returnPart;
		System.out.println("ret: ");
		System.out.println(ret);
		return ret;
	}

	public String getSearchForReachableReferencesQuery(long startId) {
		String ret = "";
		ret = "MATCH path = (d:DataObject)-[hr:has_reference]->";
		ret = ret + "(r:" + getEntityType().getSimpleName() + ")";
		ret = ret + " WITH nodes(path) as ns, r as ret WHERE id(d) = " + startId;
		ret = ret + " and NONE(node IN ns WHERE (node.deleted = TRUE)) ";
		String returnPart = getReturnPart("ret", false);
		ret = ret + returnPart;
		System.out.println("ret: ");
		System.out.println(ret);
		return ret;
	}

	public abstract Class<T> getEntityType();
}
