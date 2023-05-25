package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserSearcherTest extends BaseTestCaseIT {

	private static String searchURL;
	private static RequestSpecification requestSpecification;
	private static UserIO userIO1;
	private static UserIO userIO2;

	@BeforeAll
	public static void setUp() {
		searchURL = baseURL.concat("/" + Constants.SEARCH + "/" + Constants.USERS);
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(searchURL)
				.addHeader("X-API-KEY", jws).build();
		userIO1 = new UserIO(getNewUserWithApiKey("user1" + System.currentTimeMillis()).getUser());
		userIO2 = new UserIO(getNewUserWithApiKey("user2" + System.currentTimeMillis()).getUser());
	}

	@Test
	@Order(1)
	public void getUser1Test() {
		UserIO[] response = given().spec(requestSpecification).queryParam("username", userIO1.getUsername()).when()
				.get().then().statusCode(200).extract().as(UserIO[].class);
		assertThat(response).containsExactly(userIO1);
	}

	@Test
	@Order(2)
	public void getTestIt() {
		UserIO[] response = given().spec(requestSpecification).queryParam("email", "inte.*").when().get().then()
				.statusCode(200).extract().as(UserIO[].class);

		assertThat(response).anyMatch(user -> "test_it".equals(user.getUsername()));
	}

	@Test
	@Order(3)
	public void getUser12Test() {
		UserIO[] response = given().spec(requestSpecification).queryParam("username", ".*ser.*").when().get().then()
				.statusCode(200).extract().as(UserIO[].class);
		assertThat(response).contains(userIO1).contains(userIO2);
	}

}
