package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.util.PermissionType;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PermissionsTest extends BaseTestCaseIT {

	private static CollectionIO collection;
	private static CollectionIO collection1;
	private static CollectionIO collection2;
	private static String collectionsURL;

	private static String permissionsURL;
	private static RequestSpecification requestSpecification;
	private static RequestSpecification requestSpecification1;
	private static RequestSpecification requestSpecification2;
	private static UserWithApiKey user1;
	private static UserWithApiKey user2;
	private static String jws1;
	private static String jws2;

	@BeforeAll
	public static void setUp() {
		collectionsURL = baseURL.concat("/collections");
		collection = createCollection("PermissionsTestCollection");
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection.getId());
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws).build();
		user1 = getNewUserWithApiKey("user1");
		user2 = getNewUserWithApiKey("user2");
		jws1 = user1.getApiKey().getJws();
		jws2 = user2.getApiKey().getJws();
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws1).build();
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws2).build();
		collection1 = createCollection("PermissionsTestCollection1", user1);
		collection2 = createCollection("PermissionsTestCollection2", user2);
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

	@Test
	@Order(3)
	public void permittedGet() {
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws1).build();
		var answer = given().spec(requestSpecification1).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(answer.statusCode(), 200);
	}

	@Test
	@Order(4)
	public void notPermittedGet() {
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws2).build();
		var answer = given().spec(requestSpecification2).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(answer.statusCode(), 403);
	}

	@Test
	@Order(5)
	public void permittedGetViaPublicReadable() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.PublicReadable);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection1.getId());
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws1).build();
		given().spec(requestSpecification1).body(permissions).when().put(permissionsURL);
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws2).build();
		var answer = given().spec(requestSpecification2).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(answer.statusCode(), 200);
	}

	@Test
	@Order(6)
	public void notPermittedPutViaPublicReadable() {
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws2).build();
		var answer = given().spec(requestSpecification2).body(collection1).when()
				.put(collectionsURL + "/" + collection1.getId());
		assertEquals(answer.statusCode(), 403);
	}

	@Test
	@Order(7)
	public void permittedPutViaPublic() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Public);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection1.getId());
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws1).build();
		given().spec(requestSpecification1).body(permissions).when().put(permissionsURL);
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws2).build();
		var answer = given().spec(requestSpecification2).body(collection1).when()
				.put(collectionsURL + "/" + collection1.getId());
		assertEquals(answer.statusCode(), 200);
	}

	@Test
	@Order(8)
	public void permittedGetViaReadersList() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] { "user1" });
				setWriter(new String[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws2).build();
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws1).build();
		var answer = given().spec(requestSpecification1).when().get(collectionsURL + "/" + collection2.getId());
		assertEquals(answer.statusCode(), 200);
	}

	@Test
	@Order(9)
	public void permittedPutViaWritersList() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] { "user1" });
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws2).build();
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws1).build();
		var answer = given().spec(requestSpecification1).body(collection2).when()
				.put(collectionsURL + "/" + collection2.getId());
		assertEquals(answer.statusCode(), 200);
	}

}
