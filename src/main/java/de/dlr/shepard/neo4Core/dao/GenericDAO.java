package de.dlr.shepard.neo4Core.dao;

import java.util.Collection;
import java.util.Map;

import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.query.Pagination;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.PaginationHelper;
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
	 * @param query The query
	 * @return Iterable The result
	 */
	protected Iterable<T> findByQuery(String query, Map<String, Object> paramsMap) {
		log.debug("Run query: {}", query);
		String params = "";
		for (String key : paramsMap.keySet()) {
			params = params + "(" + key + ", " + paramsMap.get(key) + ")\n";
		}
		log.debug("queryParams: {}", params);
		Iterable<T> iter = session.query(getEntityType(), query, paramsMap);
		return iter;
	}

	protected String getParameterizedObjectPart(String variable, String type, boolean hasName) {
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

	protected String getParameterizedPaginationPart(boolean hasPagination) {
		if (hasPagination)
			return "SKIP $offset LIMIT $size";
		else
			return "";
	}

	protected String getReturnPart(String entity) {
		var result = String.format("MATCH path=(%s)-[*0..1]-() RETURN %s, nodes(path), relationships(path)", entity,
				entity);
		return result;
	}

	protected String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
		String ret;
		boolean isString = orderByAttribute.isString();
		if (!isString)
			ret = " ORDER BY " + variable + "." + orderByAttribute;
		else
			ret = " ORDER BY toLower(" + variable + "." + orderByAttribute + ")";
		boolean orderdesc;
		if (orderDesc == null)
			orderdesc = false;
		else
			orderdesc = orderDesc;
		if (orderdesc)
			ret = ret + " DESC";
		return ret;
	}

	public abstract Class<T> getEntityType();
}
