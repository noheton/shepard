package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PermissionsTest extends BaseTestCaseIT {

	private static CollectionIO collection;

	private static String permissionsURL;
	private static RequestSpecification requestSpecification;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("PermissionsTestCollection");
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection.getId());
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void getPermissionsTest() {
		var actual = given().spec(requestSpecification).when().get(permissionsURL).then().statusCode(200).extract()
				.as(PermissionsIO.class);

		assertThat(actual.getEntityId()).isEqualTo(collection.getId());
		assertThat(actual.getOwner()).isEqualTo(username);
		assertThat(actual.getReader()).isEmpty();
		assertThat(actual.getWriter()).isEmpty();
		assertThat(actual.getManager()).isEmpty();
	}

	@Test
	@Order(2)
	public void updatePermissionsTest() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] { username });
				setWriter(new String[] { username });
				setManager(new String[] { username, "invalid" });
			}
		};

		var actual = given().spec(requestSpecification).body(permissions).when().put(permissionsURL).then()
				.statusCode(200).extract().as(PermissionsIO.class);
		var expected = new PermissionsIO() {
			{
				setEntityId(collection.getId());
				setOwner(null);
				setReader(new String[] { username });
				setWriter(new String[] { username });
				setManager(new String[] { username });
			}
		};

		assertThat(actual).isEqualTo(expected);
	}

}
