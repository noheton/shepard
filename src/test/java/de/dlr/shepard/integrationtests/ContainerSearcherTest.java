package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.search.ContainerQueryType;
import de.dlr.shepard.search.ContainerSearchBody;
import de.dlr.shepard.search.ContainerSearchParams;
import de.dlr.shepard.search.ContainerSearchResult;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContainerSearcherTest extends BaseTestCaseIT {

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
		fileContainerURL = String.format("%s/%s", baseURL, Constants.FILES);
		fileContainerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(fileContainerURL).addHeader("X-API-KEY", jws).build();

		timeseriesContainerURL = String.format("%s/%s", baseURL, Constants.TIMESERIES);
		timeseriesContainerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(timeseriesContainerURL).addHeader("X-API-KEY", jws).build();

		dataContainerURL = String.format("%s/%s", baseURL, Constants.STRUCTUREDDATAS);
		dataContainerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataContainerURL).addHeader("X-API-KEY", jws).build();

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

		fileContainer1 = given().spec(fileContainerRequestSpec).body(fileContainer1).when().post().then()
				.statusCode(201).extract().as(FileContainerIO.class);
		fileContainer2 = given().spec(fileContainerRequestSpec).body(fileContainer2).when().post().then()
				.statusCode(201).extract().as(FileContainerIO.class);
		timeseriesContainer1 = given().spec(timeseriesContainerRequestSpec).body(timeseriesContainer1).when().post()
				.then().statusCode(201).extract().as(TimeseriesContainerIO.class);
		timeseriesContainer2 = given().spec(timeseriesContainerRequestSpec).body(timeseriesContainer2).when().post()
				.then().statusCode(201).extract().as(TimeseriesContainerIO.class);
		dataContainer1 = given().spec(dataContainerRequestSpec).body(dataContainer1).when().post().then()
				.statusCode(201).extract().as(StructuredDataContainerIO.class);
		dataContainer2 = given().spec(dataContainerRequestSpec).body(dataContainer2).when().post().then()
				.statusCode(201).extract().as(StructuredDataContainerIO.class);

		searchURL = baseURL.concat("/" + Constants.SEARCH + "/" + Constants.CONTAINERS);
		searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(searchURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void test1SearchFileContainers() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		String query = "{\"property\": \"name\", \"value\": \"container1\", \"operator\": \"eq\"}";
		params.setQuery(query);
		ContainerQueryType type = ContainerQueryType.FILE;
		params.setQueryType(type);
		searchBody.setSearchParams(params);
		ContainerSearchResult result = given().spec(searchRequestSpec).body(searchBody).when().post().then()
				.statusCode(200).extract().as(ContainerSearchResult.class);
		assertThat(result.getFileContainers()).contains(fileContainer1);
		assertThat(result.getFileContainers()).doesNotContain(fileContainer2);
		assertEquals(result.getStructuredDataContainers(), null);
		assertEquals(result.getTimeseriesContainers(), null);
	}

	@Test
	@Order(2)
	public void testSearchAllContainersByEquality() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		String query = "{\"property\": \"name\", \"value\": \"container1\", \"operator\": \"eq\"}";
		params.setQuery(query);
		ContainerQueryType type = null;
		params.setQueryType(type);
		searchBody.setSearchParams(params);
		ContainerSearchResult result = given().spec(searchRequestSpec).body(searchBody).when().post().then()
				.statusCode(200).extract().as(ContainerSearchResult.class);
		assertThat(result.getFileContainers()).contains(fileContainer1);
		assertThat(result.getFileContainers()).doesNotContain(fileContainer2);
		assertThat(result.getStructuredDataContainers()).contains(dataContainer1);
		assertThat(result.getStructuredDataContainers()).doesNotContain(dataContainer2);
		assertEquals(result.getTimeseriesContainers().length, 0);
	}

	@Test
	@Order(3)
	public void testSearchAllContainersByContains() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		String query = "{\"property\": \"name\", \"value\": \"ontainer1\", \"operator\": \"contains\"}";
		params.setQuery(query);
		ContainerQueryType type = null;
		params.setQueryType(type);
		searchBody.setSearchParams(params);
		ContainerSearchResult result = given().spec(searchRequestSpec).body(searchBody).when().post().then()
				.statusCode(200).extract().as(ContainerSearchResult.class);
		assertThat(result.getFileContainers()).contains(fileContainer1);
		assertThat(result.getFileContainers()).doesNotContain(fileContainer2);
		assertThat(result.getStructuredDataContainers()).contains(dataContainer1);
		assertThat(result.getStructuredDataContainers()).doesNotContain(dataContainer2);
		assertThat(result.getTimeseriesContainers()).contains(timeseriesContainer1);
		assertThat(result.getTimeseriesContainers()).doesNotContain(timeseriesContainer2);
	}

}
