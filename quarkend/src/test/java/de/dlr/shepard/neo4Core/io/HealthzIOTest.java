package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class HealthzIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(HealthzIO.class).verify();
	}

	@ParameterizedTest
	@CsvSource({ "true,false,false", "false,true,false", "false,false,true" })
	public void constructorTest(boolean neo4j, boolean mongodb, boolean influxdb) {
		var expected = new HealthzIO();
		expected.setShepard(Healthy.healthy);
		expected.setNeo4j(neo4j ? Healthy.healthy : Healthy.unhealthy);
		expected.setMongodb(mongodb ? Healthy.healthy : Healthy.unhealthy);
		expected.setInfluxdb(influxdb ? Healthy.healthy : Healthy.unhealthy);

		var actual = new HealthzIO(neo4j, mongodb, influxdb);
		assertEquals(expected, actual);
	}

}
