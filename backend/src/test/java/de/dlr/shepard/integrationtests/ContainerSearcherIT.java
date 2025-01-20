package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.container.ContainerSearchBody;
import de.dlr.shepard.common.search.container.ContainerSearchParams;
import de.dlr.shepard.common.search.container.ContainerSearchResult;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContainerSearcherIT extends BaseTestCaseIT {

  private static FileContainerIO fileContainer1;
  private static FileContainerIO fileContainer2;
  private static TimeseriesContainerIO timeseriesContainer1;
  private static TimeseriesContainerIO timeseriesContainer2;
  private static StructuredDataContainerIO dataContainer1;
  private static StructuredDataContainerIO dataContainer2;

  private static String fileContainerURL;
  private static String timeseriesContainerURL;
  private static String dataContainerURL;
  private static String searchURL;

  private static RequestSpecification fileContainerRequestSpec;
  private static RequestSpecification timeseriesContainerRequestSpec;
  private static RequestSpecification dataContainerRequestSpec;
  private static RequestSpecification searchRequestSpec;

  @BeforeAll
  public static void setUp() {
    fileContainerURL = "/" + Constants.FILE_CONTAINERS;
    fileContainerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    timeseriesContainerURL = "/" + Constants.TIMESERIES_CONTAINERS;
    timeseriesContainerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    dataContainerURL = "/" + Constants.STRUCTURED_DATA_CONTAINERS;
    dataContainerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    fileContainer1 = new FileContainerIO();
    fileContainer1.setName("container1");
    fileContainer2 = new FileContainerIO();
    fileContainer2.setName("container2");

    dataContainer1 = new StructuredDataContainerIO();
    dataContainer1.setName("container1");
    dataContainer2 = new StructuredDataContainerIO();
    dataContainer2.setName("container2");

    timeseriesContainer1 = new TimeseriesContainerIO();
    timeseriesContainer1.setName("timeseriesContainer1");
    timeseriesContainer2 = new TimeseriesContainerIO();
    timeseriesContainer2.setName("timeseriesContainer2");

    fileContainer1 = given()
      .spec(fileContainerRequestSpec)
      .body(fileContainer1)
      .when()
      .post(fileContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);
    fileContainer2 = given()
      .spec(fileContainerRequestSpec)
      .body(fileContainer2)
      .when()
      .post(fileContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);
    timeseriesContainer1 = given()
      .spec(timeseriesContainerRequestSpec)
      .body(timeseriesContainer1)
      .when()
      .post(timeseriesContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);
    timeseriesContainer2 = given()
      .spec(timeseriesContainerRequestSpec)
      .body(timeseriesContainer2)
      .when()
      .post(timeseriesContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);
    dataContainer1 = given()
      .spec(dataContainerRequestSpec)
      .body(dataContainer1)
      .when()
      .post(dataContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);
    dataContainer2 = given()
      .spec(dataContainerRequestSpec)
      .body(dataContainer2)
      .when()
      .post(dataContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);

    searchURL = "/" + Constants.SEARCH + "/" + Constants.CONTAINERS;
    searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws).build();
  }

  @Test
  @Order(1)
  public void test1SearchFileContainers() {
    String query = "{\"property\": \"name\", \"value\": \"container1\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(query, ContainerType.FILE);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    ContainerSearchResult result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ContainerSearchResult.class);
    var notExpected = List.of(
      fileContainer2.getId(),
      dataContainer1.getId(),
      dataContainer2.getId(),
      timeseriesContainer1.getId(),
      timeseriesContainer2.getId()
    );
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(fileContainer1.getId()));
    assertThat(result.getResults()).noneMatch(res -> notExpected.contains(res.getId()));
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  @Order(2)
  public void testSearchStructuredDataContainersByContains() {
    String query = "{\"property\": \"name\", \"value\": \"ontainer1\", \"operator\": \"contains\"}";
    ContainerSearchParams params = new ContainerSearchParams(query, ContainerType.STRUCTUREDDATA);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    ContainerSearchResult result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ContainerSearchResult.class);
    var notExpected = List.of(
      fileContainer1.getId(),
      fileContainer2.getId(),
      dataContainer2.getId(),
      timeseriesContainer1.getId(),
      timeseriesContainer2.getId()
    );
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(dataContainer1.getId()));
    assertThat(result.getResults()).noneMatch(res -> notExpected.contains(res.getId()));
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  @Order(2)
  public void testSearchTimeseriesContainersByContains() {
    String query = "{\"property\": \"name\", \"value\": \"ontainer1\", \"operator\": \"contains\"}";
    ContainerSearchParams params = new ContainerSearchParams(query, ContainerType.TIMESERIES);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    ContainerSearchResult result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ContainerSearchResult.class);
    var notExpected = List.of(
      fileContainer1.getId(),
      fileContainer2.getId(),
      dataContainer1.getId(),
      dataContainer2.getId(),
      timeseriesContainer2.getId()
    );
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(timeseriesContainer1.getId()));
    assertThat(result.getResults()).noneMatch(res -> notExpected.contains(res.getId()));
    assertThat(result.getSearchParams()).isEqualTo(params);
  }
}
