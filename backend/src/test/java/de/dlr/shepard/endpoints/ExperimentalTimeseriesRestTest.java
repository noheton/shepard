package de.dlr.shepard.endpoints;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExperimentalTimeseriesRestTest {

  @Test
  public void testGetAllTimeseriesesContainersEndpoint() {
    // Todo: Solve authentication problem
    given().when().get("/experimental-timeseriesContainers").then().statusCode(401);
  }
}
