package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.influxDB.InfluxPoint;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeseriesTest extends BaseTestCaseIT {
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;

	private static TimeseriesContainerIO container;
	private static TimeseriesPayload payload;
	private static long start;
	private static long end;

	private static int numPoints = 32;

	@BeforeAll
	public static void setUp() {
		containerURL = String.format("%s/%s", baseURL, Constants.TIMESERIES);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void createTimeseriesContainer() {
		var toCreate = new TimeseriesContainerIO();
		toCreate.setName("TimeseriesContainer");

		var actual = given().spec(containerRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(TimeseriesContainerIO.class);
		container = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDatabase()).isNotNull();
		assertThat(actual.getName()).isEqualTo("TimeseriesContainer");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void getTimeseriesContainers() {
		var actual = given().spec(containerRequestSpec).when().get().then().statusCode(200).extract()
				.as(TimeseriesContainerIO[].class);

		assertThat(actual).contains(container);
	}

	@Test
	@Order(3)
	public void getTimeseriesContainer() {
		var actual = given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then()
				.statusCode(200).extract().as(TimeseriesContainerIO.class);

		assertThat(actual).isEqualTo(container);
	}

	@Test
	@Order(4)
	public void createTimeseries() {
		var currentTime = System.currentTimeMillis() * 1000000;
		var slice = (2f * Math.PI) / (numPoints - 1);

		List<InfluxPoint> points = new ArrayList<>();
		for (int i = 0; i < numPoints; i++) {
			var offset = i * 1000000000L;
			var point = new InfluxPoint(currentTime + offset, Math.sin(slice * i));
			points.add(point);
		}

		start = points.get(0).getTimeInNanoseconds();
		end = points.get(numPoints - 1).getTimeInNanoseconds();

		payload = new TimeseriesPayload();
		payload.setTimeseries(new Timeseries("meas", "dev", "loc", "symName", "field"));
		payload.setPoints(points);

		var actual = given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD)).then()
				.statusCode(201).extract().as(Timeseries.class);

		assertThat(actual).isEqualTo(payload.getTimeseries());
	}

	@Test
	@Order(5)
	public void getTimeseriesAvailable() {
		var actual = given().spec(containerRequestSpec).when()
				.get(containerURL + "/" + container.getId() + "/" + Constants.AVAILABLE).then().statusCode(200)
				.extract().as(Timeseries[].class);

		assertThat(actual).contains(new Timeseries("meas", "dev", "loc", "symName", null));
	}

	@Test
	@Order(6)
	public void getTimeseriesPayload() {
		var actual = given().spec(containerRequestSpec).when()
				.queryParams(Map.of("measurement", "meas", "location", "loc", "device", "dev", "symbolic_name",
						"symName", "field", "field", "start", start, "end", end))
				.get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD).then().statusCode(200).extract()
				.as(TimeseriesPayload.class);

		assertThat(actual).isEqualTo(payload);
	}

	@Test
	@Order(7)
	public void deleteContainer() {
		given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);
		given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
	}

}
