package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.influxDB.InfluxPoint;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeseriesReferenceTest extends BaseTestCaseIT {
	private static CollectionIO collection;
	private static DataObjectIO dataObject;

	private static String referencesURL;
	private static RequestSpecification referencesRequestSpec;
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;

	private static TimeseriesContainerIO container;
	private static TimeseriesReferenceIO reference;
	private static TimeseriesPayload payload;

	private static int numPoints = 32;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("TimeseriesReferenceTestCollection");
		dataObject = createDataObject("TimeseriesReferenceTestDataObject", collection.getId());

		referencesURL = String.format("%s/collections/%d/dataObjects/%d/timeseriesReferences", baseURL,
				collection.getId(), dataObject.getId());
		referencesRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(referencesURL)
				.addHeader("X-API-KEY", jws).build();

		containerURL = String.format("%s/timeseries", baseURL);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();

		var toCreate = new TimeseriesContainerIO();
		toCreate.setName("TimeseriesContainer");
		container = given().spec(containerRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(TimeseriesContainerIO.class);

		var currentTime = System.currentTimeMillis() * 1000000;
		var slice = (2f * Math.PI) / (numPoints - 1);

		List<InfluxPoint> points = new ArrayList<>();
		for (int i = 0; i < numPoints; i++) {
			var offset = i * 1000000000L;
			var point = new InfluxPoint(currentTime + offset, Math.sin(slice * i));
			points.add(point);
		}

		payload = new TimeseriesPayload();
		payload.setTimeseries(new Timeseries("meas", "dev", "loc", "symName", "field"));
		payload.setPoints(points);

		given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/payload", containerURL, container.getId())).then().statusCode(201);
	}

	@Test
	@Order(1)
	public void createTimeseriesReference() {
		var nanos = payload.getPoints().get(0).getTimeInNanoseconds();
		var toCreate = new TimeseriesReferenceIO();
		toCreate.setName("TimeseriesReferenceDummy");
		toCreate.setStart(nanos - 1000000000L);
		toCreate.setEnd(nanos + 1000000000L * numPoints);
		toCreate.setTimeseries(List.of(payload.getTimeseries()));
		toCreate.setTimeseriesContainerId(container.getId());

		var actual = given().spec(referencesRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(TimeseriesReferenceIO.class);
		reference = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getStart()).isEqualTo(nanos - 1000000000L);
		assertThat(actual.getEnd()).isEqualTo(nanos + 1000000000L * numPoints);
		assertThat(actual.getName()).isEqualTo("TimeseriesReferenceDummy");
		assertThat(actual.getTimeseries()).isEqualTo(List.of(payload.getTimeseries()));
		assertThat(actual.getType()).isEqualTo("TimeseriesReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void getTimeseriesReferences() {
		var actual = given().spec(referencesRequestSpec).when().get().then().statusCode(200).extract()
				.as(TimeseriesReferenceIO[].class);

		assertThat(actual).containsExactly(reference);
	}

	@Test
	@Order(3)
	public void getTimeseriesReference() {
		var actual = given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then()
				.statusCode(200).extract().as(TimeseriesReferenceIO.class);

		assertThat(actual).isEqualTo(reference);
	}

	@Test
	@Order(4)
	public void getTimeseriesReferencePayload() {
		var actual = given().spec(referencesRequestSpec).when()
				.get(String.format("%s/%d/payload", referencesURL, reference.getId())).then().statusCode(200).extract()
				.as(TimeseriesPayload[].class);

		assertThat(actual).containsExactly(payload);
	}

	@Test
	@Order(5)
	public void deleteReferences() {
		given().spec(referencesRequestSpec).when().delete(referencesURL + "/" + reference.getId()).then()
				.statusCode(204);

		given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
	}

}
