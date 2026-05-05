package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.List;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class HealthzIT extends BaseTestCaseIT {

  private static final String healthURL = "/" + Constants.HEALTHZ;
  private static final String readyURL = "/" + Constants.HEALTHZ + "/ready";
  private static final String startedURL = "/" + Constants.HEALTHZ + "/started";
  private static final String liveURL = "/" + Constants.HEALTHZ + "/live";

  @Test
  public void getHealthz() {
    List<String> expectedReadinessChecks = List.of("neo4j-readiness", "mongodb-readiness", "timescaledb-readiness");

    var actual = given().when().get(healthURL).then().statusCode(200).extract().as(HealthzIO.class);
    assertEquals(HealthCheckResponse.Status.UP, actual.getStatus());
    var names = actual.getChecks().stream().map(ServiceHealthCheckIO::getName).toList();
    assertTrue(names.containsAll(expectedReadinessChecks), "missing readiness checks: " + names);
    assertTrue(names.contains("jvm-liveness"), "missing jvm-liveness check");
  }

  @Test
  public void getReadinessOnly() {
    var actual = given().when().get(readyURL).then().statusCode(200).extract().as(HealthzIO.class);
    assertEquals(HealthCheckResponse.Status.UP, actual.getStatus());
  }

  @Test
  public void getStartupOnly() {
    var actual = given().when().get(startedURL).then().statusCode(200).extract().as(HealthzIO.class);
    assertEquals(HealthCheckResponse.Status.UP, actual.getStatus());
  }

  @Test
  public void getLivenessOnly() {
    var actual = given().when().get(liveURL).then().statusCode(200).extract().as(HealthzIO.class);
    assertEquals(HealthCheckResponse.Status.UP, actual.getStatus());
  }
}
