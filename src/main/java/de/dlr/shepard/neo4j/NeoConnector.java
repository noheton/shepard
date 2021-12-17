package de.dlr.shepard.neo4j;

import org.neo4j.ogm.config.AutoIndexMode;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.exception.ConnectionException;
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
		PropertiesHelper helper = new PropertiesHelper();
		String username = helper.getProperty("neo4j.username");
		String password = helper.getProperty("neo4j.password");
		String host = helper.getProperty("neo4j.host");
		String connectionString = String.format("bolt://%s:%s@%s", username, password, host);
		// TODO: How to autoIndex without deprecation warnings?
		String autoIndexMode = AutoIndexMode.ASSERT.getName();
		Configuration configuration = new Configuration.Builder().uri(connectionString).autoIndex(autoIndexMode)
				.verifyConnection(true).useNativeTypes().build();
		while (true) {
			try {
				sessionFactory = new SessionFactory(configuration, "de.dlr.shepard.neo4Core.entities");
				return true;
			} catch (ConnectionException ex) {
				log.warn("Cannot connect to neo4j database. Retrying...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					log.info("Cannot sleep while waiting for neo4j Connection");
				}
			}
		}
	}

	@Override
	public boolean disconnect() {
		if (sessionFactory != null)
			sessionFactory.close();
		return true;
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
