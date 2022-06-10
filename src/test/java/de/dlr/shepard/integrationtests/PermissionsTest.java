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
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.security.PermissionType;
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
	private static String userGroupURL;
	private static RequestSpecification requestSpecification;
	private static RequestSpecification requestSpecification1;
	private static RequestSpecification requestSpecification2;
	private static RequestSpecification requestSpecification3;
	private static RequestSpecification userGroupSpecification;
	private static UserWithApiKey user1;
	private static UserWithApiKey user2;
	private static UserWithApiKey user3;
	private static UserWithApiKey user4;
	private static String jws1;
	private static String jws2;
	private static String jws3;

	@BeforeAll
	public static void setUp() {
		collectionsURL = baseURL.concat("/collections");
		collection = createCollection("PermissionsTestCollection");
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection.getId());
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws).build();
		user1 = getNewUserWithApiKey("user1");
		user2 = getNewUserWithApiKey("user2");
		user3 = getNewUserWithApiKey("user3");
		user4 = getNewUserWithApiKey("user4");
		jws1 = user1.getApiKey().getJws();
		jws2 = user2.getApiKey().getJws();
		jws3 = user3.getApiKey().getJws();
		requestSpecification1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws1).build();
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws2).build();
		requestSpecification3 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws3).build();
		collection1 = createCollection("PermissionsTestCollection1", user1);
		collection2 = createCollection("PermissionsTestCollection2", user2);
		userGroupURL = String.format("%s/usergroup", baseURL);
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
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[] { username });
			}
		};

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	@Order(3)
	public void permittedGet() {
		var answer = given().spec(requestSpecification1).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(4)
	public void getRolesOwner() {
		var actual = given().spec(requestSpecification1).when()
				.get(collectionsURL + "/" + collection1.getId() + "/roles").as(RolesIO.class);
		var expected = new RolesIO(true, false, false, false);
		assertEquals(expected, actual);
	}

	@Test
	@Order(5)
	public void notPermittedGet() {
		var answer = given().spec(requestSpecification2).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(403, answer.statusCode());
	}

	@Test
	@Order(6)
	public void permittedGetViaPublicReadable() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.PublicReadable);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection1.getId());
		given().spec(requestSpecification1).body(permissions).when().put(permissionsURL);
		var answer = given().spec(requestSpecification2).when().get(collectionsURL + "/" + collection1.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(7)
	public void getRolesReader() {
		var actual = given().spec(requestSpecification1).when()
				.get(collectionsURL + "/" + collection1.getId() + "/roles").as(RolesIO.class);
		var expected = new RolesIO(false, false, false, true);
		assertEquals(expected, actual);
	}

	@Test
	@Order(8)
	public void notPermittedPutViaPublicReadable() {
		var answer = given().spec(requestSpecification2).body(collection1).when()
				.put(collectionsURL + "/" + collection1.getId());
		assertEquals(403, answer.statusCode());
	}

	@Test
	@Order(9)
	public void permittedPutViaPublic() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Public);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection1.getId());
		given().spec(requestSpecification1).body(permissions).when().put(permissionsURL);
		var answer = given().spec(requestSpecification2).body(collection1).when()
				.put(collectionsURL + "/" + collection1.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(10)
	public void getRolesReaderWriter() {
		var actual = given().spec(requestSpecification2).when()
				.get(collectionsURL + "/" + collection1.getId() + "/roles").as(RolesIO.class);
		var expected = new RolesIO(false, false, true, true);
		assertEquals(expected, actual);
	}

	@Test
	@Order(11)
	public void permittedGetViaReadersList() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] { "user1" });
				setWriter(new String[] {});
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);
		var answer = given().spec(requestSpecification1).when().get(collectionsURL + "/" + collection2.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(12)
	public void permittedPutViaWritersList() {
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] { "user1" });
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);
		var answer = given().spec(requestSpecification1).body(collection2).when()
				.put(collectionsURL + "/" + collection2.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(13)
	public void notPermittedGetViaReadersGroup() {
		UserGroupIO readersGroup = new UserGroupIO();
		readersGroup.setName("readersGroup");
		readersGroup.setUsernames(new String[] { user4.getUser().getUsername() });
		userGroupSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(userGroupURL)
				.addHeader("X-API-KEY", jws2).build();
		readersGroup = given().spec(userGroupSpecification).body(readersGroup).when().post(userGroupURL).then()
				.statusCode(201).extract().as(UserGroupIO.class);
		long[] readerGroupIds = { readersGroup.getId() };
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(readerGroupIds);
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		requestSpecification2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(permissionsURL)
				.addHeader("X-API-KEY", jws2).build();
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);

		requestSpecification3 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws3).build();
		var answer = given().spec(requestSpecification3).when().get(collectionsURL + "/" + collection2.getId());
		assertEquals(403, answer.statusCode());
	}

	@Test
	@Order(14)
	public void permittedGetViaReadersGroup() {
		UserGroupIO readersGroup = new UserGroupIO();
		readersGroup.setName("readersGroup1");
		readersGroup.setUsernames(new String[] { "user3" });
		userGroupSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(userGroupURL)
				.addHeader("X-API-KEY", jws2).build();
		readersGroup = given().spec(userGroupSpecification).body(readersGroup).when().post(userGroupURL).then()
				.statusCode(201).extract().as(UserGroupIO.class);
		long[] readerGroupIds = { readersGroup.getId() };
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(readerGroupIds);
				setWriterGroupIds(new long[] {});
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);

		var answer = given().spec(requestSpecification3).when().get(collectionsURL + "/" + collection2.getId());
		assertEquals(200, answer.statusCode());
	}

	@Test
	@Order(15)
	public void notPermittedPutViaWritersGroup() {
		UserGroupIO writersGroup = new UserGroupIO();
		writersGroup.setName("writersGroup");
		writersGroup.setUsernames(new String[] { user4.getUser().getUsername() });
		userGroupSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(userGroupURL)
				.addHeader("X-API-KEY", jws2).build();
		writersGroup = given().spec(userGroupSpecification).body(writersGroup).when().post(userGroupURL).then()
				.statusCode(201).extract().as(UserGroupIO.class);
		long[] writersGroupIds = { writersGroup.getId() };
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(writersGroupIds);
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);

		var answer = given().spec(requestSpecification3).body(collection2).when()
				.put(collectionsURL + "/" + collection2.getId());
		assertEquals(403, answer.statusCode());
	}

	@Test
	@Order(16)
	public void permittedPutViaWritersGroup() {
		UserGroupIO writersGroup = new UserGroupIO();
		writersGroup.setName("writersGroup1");
		writersGroup.setUsernames(new String[] { "user3" });
		userGroupSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(userGroupURL)
				.addHeader("X-API-KEY", jws2).build();
		writersGroup = given().spec(userGroupSpecification).body(writersGroup).when().post(userGroupURL).then()
				.statusCode(201).extract().as(UserGroupIO.class);
		long[] writersGroupIds = { writersGroup.getId() };
		var permissions = new PermissionsIO() {
			{
				setReader(new String[] {});
				setWriter(new String[] {});
				setReaderGroupIds(new long[] {});
				setWriterGroupIds(writersGroupIds);
				setManager(new String[] {});
				setPermissionType(PermissionType.Private);
			}
		};
		permissionsURL = String.format("%s/collections/%d/permissions", baseURL, collection2.getId());
		given().spec(requestSpecification2).body(permissions).when().put(permissionsURL);

		var answer = given().spec(requestSpecification3).body(collection2).when()
				.put(collectionsURL + "/" + collection2.getId());
		assertEquals(200, answer.statusCode());
	}

}
