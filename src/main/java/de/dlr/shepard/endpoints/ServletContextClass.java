package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.InfluxConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PKIHelper;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class ServletContextClass implements ServletContextListener {

	private static IConnector neo4j = NeoConnector.getInstance();
	private static IConnector mongodb = MongoDBConnector.getInstance();
	private static IConnector influx = InfluxConnector.getInstance();

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		var pkiHelper = new PKIHelper();
		pkiHelper.init();

		neo4j.connect();
		mongodb.connect();
		influx.connect();
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		neo4j.disconnect();
		mongodb.disconnect();
		influx.disconnect();
	}

}
