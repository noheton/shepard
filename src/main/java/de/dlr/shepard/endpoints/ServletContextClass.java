package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.InfluxDBConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4j.MigrationsRunner;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PKIHelper;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServletContextClass implements ServletContextListener {

	private static IConnector neo4j = NeoConnector.getInstance();
	private static IConnector mongodb = MongoDBConnector.getInstance();
	private static IConnector influxdb = InfluxDBConnector.getInstance();

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		log.info("Starting shepard backend");
		var pkiHelper = new PKIHelper();
		var migrationRunner = new MigrationsRunner();
		pkiHelper.init();

		log.info("Waiting for databases");
		migrationRunner.waitForConnection();

		log.info("Run database migrations");
		migrationRunner.apply();

		log.info("Initialize databases");
		neo4j.connect();
		mongodb.connect();
		influxdb.connect();
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		neo4j.disconnect();
		mongodb.disconnect();
		influxdb.disconnect();
	}

}
