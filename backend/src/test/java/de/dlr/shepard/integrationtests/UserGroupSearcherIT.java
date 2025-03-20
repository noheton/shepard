package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.search.io.UserGroupSearchBody;
import de.dlr.shepard.common.search.io.UserGroupSearchParams;
import de.dlr.shepard.common.search.io.UserGroupSearchResult;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserGroupSearcherIT extends BaseTestCaseIT {

  private static String searchURL;
  private static UserGroupIO userGroupIO1;
  private static UserGroupIO userGroupIO2;

  @BeforeAll
  public static void setUp() {
    searchURL = "/" + Constants.SEARCH + "/" + Constants.USERGROUPS;
    userGroupIO1 = new UserGroupIO(getNewUserGroup("userGroup1" + System.currentTimeMillis()));
    userGroupIO2 = new UserGroupIO(getNewUserGroup("userGroup2" + System.currentTimeMillis()));
  }

  @Test
  @Order(1)
  public void findOneUserGroupTest() {
    String query = "{\"property\": \"name\", \"value\": \"userGroup1\", \"operator\": \"contains\"}";
    var params = new UserGroupSearchParams(query);
    var searchBody = new UserGroupSearchBody(params);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupSearchResult.class);
    assertThat(result.getResults()).contains(userGroupIO1);
    assertThat(result.getResults()).doesNotContain(userGroupIO2);
  }

  @Test
  @Order(2)
  public void findTwoUserGroupsTest() {
    String query = "{\"property\": \"name\", \"value\": \"userGroup\", \"operator\": \"contains\"}";
    var params = new UserGroupSearchParams(query);
    var searchBody = new UserGroupSearchBody(params);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserGroupSearchResult.class);
    assertThat(result.getResults()).contains(userGroupIO1, userGroupIO2);
  }
}
