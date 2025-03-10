package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserGroupIT extends BaseTestCaseIT {

  private static String userGroupURL;
  private static UserGroupIO userGroupCreated;
  private static UserGroupIO userGroupChanged;

  @BeforeAll
  public static void setUp() {
    userGroupURL = "/" + Constants.USERGROUPS;
  }

  @Test
  @Order(1)
  public void createUserGroup() {
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { otherUser.getUser().getUsername() });

    userGroupCreated = given()
      .spec(requestSpecOfDefaultUser)
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
    assertThat(userGroupCreated.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(userGroupCreated.getName()).isEqualTo("userGroup");
    assertThat(userGroupCreated.getUsernames()).containsExactly(otherUser.getUser().getUsername());
    assertThat(userGroupCreated.getUpdatedAt()).isNull();
    assertThat(userGroupCreated.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getUserGroup() {
    UserGroupIO userGroup = given()
      .spec(requestSpecOfDefaultUser)
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
  public void getUserGroup_doesNotExist_notFound() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(userGroupURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(response.getMessage()).isEqualTo("ID ERROR - UserGroup does not exist");
  }

  @Test
  @Order(4)
  public void getAllUserGroups() {
    UserGroupIO[] allUserGroups = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(userGroupURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupIO[].class);
    assertThat(allUserGroups).contains(userGroupCreated);
  }

  @Test
  @Order(5)
  public void putUserGroup() {
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("changedUserGroup");
    userGroup.setUsernames(new String[] { nameOfDefaultUser });
    userGroup.setId(userGroupCreated.getId());
    userGroupChanged = given()
      .spec(requestSpecOfDefaultUser)
      .body(userGroup)
      .when()
      .put(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupIO.class);
    assertThat(userGroupChanged.getName()).isEqualTo("changedUserGroup");
    assertThat(userGroupChanged.getUsernames()).containsExactly(nameOfDefaultUser);
    assertThat(userGroupChanged.getUpdatedAt()).isNotNull();
    assertThat(userGroupChanged.getUpdatedBy()).isEqualTo(nameOfDefaultUser);
  }

  @Test
  @Order(6)
  public void deleteUserGroup() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(204);
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(userGroupURL + "/" + userGroupCreated.getId())
      .then()
      .statusCode(404);
  }
}
