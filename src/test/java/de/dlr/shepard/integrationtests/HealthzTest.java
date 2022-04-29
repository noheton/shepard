package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.dlr.shepard.neo4Core.io.HealthzIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class HealthzTest extends BaseTestCaseIT {

	private static String healthURL;
	private static RequestSpecification requestSpecification;

	@BeforeAll
	public static void setUp() {
		healthURL = baseURL + "/healthz";
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(healthURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	public void getHealthcheck() {
		var expected = new HealthzIO(true, true, true);
		var actual = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(HealthzIO.class);
		assertEquals(expected, actual);

	}

}
