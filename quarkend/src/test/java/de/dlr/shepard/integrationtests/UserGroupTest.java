package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserGroupTest extends BaseTestCaseIT {

  private static UserWithApiKey user;
  private static UserWithApiKey user1;
  private static String jws;
  private static RequestSpecification userGroupSpecification;
  private static String userGroupURL;
  private static UserGroupIO userGroupCreated;
  private static UserGroupIO userGroupChanged;

  @BeforeAll
  public static void setUp() {
    userGroupURL = "/" + Constants.USERGROUP;
    user = getNewUserWithApiKey("user");
    user1 = getNewUserWithApiKey("user1");
    jws = user.getApiKey().getJws();
  }

  @Test
  @Order(1)
  public void createUserGroup() {
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { user1.getUser().getUsername() });
    userGroupSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    userGroupCreated = given()
      .spec(userGroupSpecification)
      .body(userGroup)
      .when()
      .post(userGroupURL)
      .then()
      .statusCode(201)
      .extract()
      .as(UserGroupIO.class);
    userGroup = userGroupCreated;

    assertThat(userGroupCreated.getId()).isNotNull();
    assertThat(userGroupCreated.getCreatedAt()).isNotNull();
    assertThat(userGroupCreated.getCreatedBy()).isEqualTo("user");
    assertThat(userGroupCreated.getName()).isEqualTo("userGroup");
    assertThat(userGroupCreated.getUsernames()).containsExactly("user1");
    assertThat(userGroupCreated.getUpdatedAt()).isNull();
    assertThat(userGroupCreated.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getUserGroup() {
    UserGroupIO userGroup = given()
      .spec(userGroupSpecification)
      .when()
      .get(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupIO.class);
    assertEquals(userGroupCreated, userGroup);
  }

  @Test
  @Order(3)
  public void getAllUserGroups() {
    UserGroupIO[] allUserGroups = given()
      .spec(userGroupSpecification)
      .when()
      .get(userGroupURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupIO[].class);
    assertThat(allUserGroups).contains(userGroupCreated);
  }

  @Test
  @Order(4)
  public void putUserGroup() {
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("changedUserGroup");
    userGroup.setUsernames(new String[] { "user" });
    userGroup.setId(userGroupCreated.getId());
    userGroupChanged = given()
      .spec(userGroupSpecification)
      .body(userGroup)
      .when()
      .put(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupIO.class);
    assertThat(userGroupChanged.getName()).isEqualTo("changedUserGroup");
    assertThat(userGroupChanged.getUsernames()).containsExactly("user");
    assertThat(userGroupChanged.getUpdatedAt()).isNotNull();
    assertThat(userGroupChanged.getUpdatedBy()).isEqualTo("user");
  }

  @Test
  @Order(5)
  public void deleteUserGroup() {
    given()
      .spec(userGroupSpecification)
      .when()
      .delete(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(204);
    given()
      .spec(userGroupSpecification)
      .when()
      .get(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(404);
  }
}
