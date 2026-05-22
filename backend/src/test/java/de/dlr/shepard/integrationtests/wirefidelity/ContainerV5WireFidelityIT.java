package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity tests for the three container kinds —
 * {@code /shepard/api/{fileContainers,timeseriesContainers,structuredDataContainers}}.
 *
 * <p>Each container kind extends {@code BasicContainerIO} (which extends
 * {@code BasicEntityIO}) and adds zero or one fields ({@code oid} on
 * {@code FileContainerIO} and {@code StructuredDataContainerIO}). The fixtures isolate
 * the BasicContainerIO contract — adding a new field to BasicContainerIO without an
 * upstream-compat policy entry breaks all three fixtures at once, which is exactly
 * the signal this corpus exists to surface.
 */
@QuarkusIntegrationTest
public class ContainerV5WireFidelityIT extends V5WireFidelityTest {

  @Test
  public void createFileContainer_wireMatchesFixture() {
    var payload = new FileContainerIO();
    payload.setName("FileContainerWireFixture");
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("/" + Constants.FILE_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .response();
    assertWireMatches("filecontainers", "create", response);
  }

  @Test
  public void createTimeseriesContainer_wireMatchesFixture() {
    var payload = new TimeseriesContainerIO();
    payload.setName("TimeseriesContainerWireFixture");
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("/" + Constants.TIMESERIES_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .response();
    assertWireMatches("timeseriescontainers", "create", response);
  }

  @Test
  public void createStructuredDataContainer_wireMatchesFixture() {
    var payload = new StructuredDataContainerIO();
    payload.setName("StructuredDataContainerWireFixture");
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("/" + Constants.STRUCTURED_DATA_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .response();
    assertWireMatches("structureddatacontainers", "create", response);
  }
}
