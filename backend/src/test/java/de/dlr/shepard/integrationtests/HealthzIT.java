package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.List;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class HealthzIT extends BaseTestCaseIT {

  private static String healthURL = "/" + Constants.HEALTHZ;

  @Test
  public void getHealthz() {
    List<String> expectedServices = List.of("Neo4J", "MongoDB", "InfluxDB", "TimescaleDB");

    assertNotNull(expectedServices);

    var expected = HealthzIO.createInstanceWithCheckedServices(HealthCheckResponse.Status.UP, expectedServices);
    var actual = given().when().get(healthURL).then().statusCode(200).extract().as(HealthzIO.class);
    assertEquals(expected.getStatus(), actual.getStatus());
    assertTrue(actual.getChecks().containsAll(expected.getChecks()));
    assertTrue(expected.getChecks().containsAll(actual.getChecks()));
  }
}
