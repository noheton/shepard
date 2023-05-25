package de.dlr.shepard.neo4j;

import java.util.Collections;

import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.exception.ConnectionException;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PropertiesHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * Connector for read and write access to the Neo4J database. The class
 * represents the lowest level of data access to the Neo4J database.
 *
 */
@Slf4j
public class NeoConnector implements IConnector {
	private SessionFactory sessionFactory = null;
	private static NeoConnector instance = null;

	/**
	 * Private constructor
	 */
	private NeoConnector() {
	}

	/**
	 * Returns the one and only instance of a NeoConnector
	 *
	 * @return NeoConnector
	 */
	public static NeoConnector getInstance() {
		if (instance == null) {
			instance = new NeoConnector();
		}
		return instance;
	}

	/**
	 * Establishes a connection to the Neo4J server by using the URL saved in the
	 * config.properties file returned by the DatabaseHelper. This will block until
	 * a connection could be established.
	 */
	@Override
	public boolean connect() {
		var pHelper = new PropertiesHelper();
		String username = pHelper.getProperty("neo4j.username");
		String password = pHelper.getProperty("neo4j.password");
		String host = pHelper.getProperty("neo4j.host");
		Configuration configuration = new Configuration.Builder().uri("neo4j://" + host).credentials(username, password)
				.verifyConnection(true).useNativeTypes().build();
		while (true) {
			try {
				sessionFactory = new SessionFactory(configuration, "de.dlr.shepard.neo4Core.entities",
						"de.dlr.shepard.influxDB", "de.dlr.shepard.mongoDB");
				return true;
			} catch (ConnectionException ex) {
				log.warn("Cannot connect to neo4j database. Retrying...");
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Cannot sleep while waiting for neo4j Connection");
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public boolean disconnect() {
		if (sessionFactory != null)
			sessionFactory.close();
		return true;
	}

	@Override
	public boolean alive() {
		Result result;
		try {
			result = sessionFactory.openSession().query("MATCH (n) RETURN count(*) as count", Collections.emptyMap());
		} catch (ConnectionException ex) {
			return false;
		}
		return result.iterator().hasNext() && result.iterator().next().containsKey("count");
	}

	/**
	 * Returns the internal neo4j session
	 *
	 * @return the internal neo4j session
	 */
	public Session getNeo4jSession() {
		if (sessionFactory == null) {
			return null;
		}
		return sessionFactory.openSession();
	}
}
