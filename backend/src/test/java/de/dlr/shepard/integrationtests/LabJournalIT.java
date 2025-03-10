package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LabJournalIT extends BaseTestCaseIT {

  private static String labJournalURL;
  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static LabJournalEntryIO labJournal;

  private static String validJournalEntryHtml =
    """
    <h3>This is my heading</h3>
    <p>Here some <strong>bold text</strong>, some <em>italic text</em>, some <u>underline text</u>, some <code>code text</code></p>
    <p>left</p><p style="text-align: center">center</p><p style="text-align: right">right</p><p></p>
    <p><a target="_blank" rel="noopener noreferrer nofollow" href="https://shepard.xyz">This is a link</a></p>
    <p></p>
    <ul><li><p>List 1</p><ul><li><p>List 2</p></li></ul></li></ul>
    <ol><li><p>List 1.1</p><ol><li><p>List 2.2</p></li></ol></li></ol><p></p>
    <table style="min-width: 75px"><colgroup><col style="min-width: 25px">
    <col style="min-width: 25px">
    <col style="min-width: 25px"></colgroup><tbody><tr><th colspan="1" rowspan="1"><p>1</p></th>
    <th colspan="1" rowspan="1"><p>2</p></th><th colspan="1" rowspan="1"><p>3</p></th></tr><tr>
    <td colspan="1" rowspan="1"><p>3</p></td><td colspan="1" rowspan="1"><p>2</p></td>
    <td colspan="1" rowspan="1"><p>1</p></td></tr><tr><td colspan="1" rowspan="1"><p>c</p></td>
    <td colspan="1" rowspan="1"><p>b</p></td><td colspan="1" rowspan="1"><p>a</p></td></tr></tbody></table>
    """;

  private static String invalidJournalEntryHtml =
    """
    <h1>This is my heading</h1>
    <p>Here some <strong>bold text</strong>, some <em>italic text</em>, some <u>underline text</u>, some <code>code text</code></p>
    <p>left</p><p style="text-align: center">center</p><p style="text-align: right">right</p><p></p>
    <p><a target="_blank" rel="noopener noreferrer nofollow" href="https://shepard.xyz">This is a link</a></p>
    <p></p>
    <ul><li><p>List 1</p><ul><li><p>List 2</p></li></ul></li></ul>
    <ol><li><p>List 1.1</p><ol><li><p>List 2.2</p></li></ol></li></ol><p></p>
    <table style="min-width: 75px"><colgroup><col style="min-width: 25px">
    <col style="min-width: 25px">
    <script>alert("dangerous")</script>
    """;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("labJournalTestCollection");
    dataObject = createDataObject("TimeseriesReferenceTestDataObject", collection.getId());

    labJournalURL = "/" + Constants.LAB_JOURNAL_ENTRIES;
  }

  @Test
  @Order(1)
  public void postLabJournal_Success() {
    LabJournalEntryIO payload = new LabJournalEntryIO();
    payload.setJournalContent(validJournalEntryHtml);
    LabJournalEntryIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .queryParam("dataObjectId", dataObject.getId())
      .when()
      .post(labJournalURL)
      .then()
      .statusCode(201)
      .extract()
      .as(LabJournalEntryIO.class);
    labJournal = actual;
  }

  @Test
  @Order(2)
  public void postLabJournal_Failure_invalidHtml() {
    LabJournalEntryIO payload = new LabJournalEntryIO();
    payload.setJournalContent(invalidJournalEntryHtml);
    given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .queryParam("dataObjectId", dataObject.getId())
      .when()
      .post(labJournalURL)
      .then()
      .statusCode(400);
  }

  @Test
  @Order(3)
  public void getLabJournal_exists_success() {
    LabJournalEntryIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(labJournalURL + "/" + labJournal.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(LabJournalEntryIO.class);

    assertThat(actual).isEqualTo(labJournal);
  }

  @Test
  @Order(4)
  public void getLabJournal_doesNotExist_notFound() {
    ErrorResponse actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(labJournalURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - LabJournalEntry does not exist");
  }

  @Test
  // Disabled since this needs to be fixed in https://gitlab.com/dlr-shepard/shepard/-/issues/508
  @Disabled
  @Order(5)
  public void getLabJournals_noQueryParam_badRequest() {
    given().spec(requestSpecOfDefaultUser).when().get(labJournalURL).then().statusCode(400);
  }

  @Test
  @Order(6)
  public void getLabJournals_dataObjectDoesExist_success() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(labJournalURL + "?dataObjectId=" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(LabJournalEntryIO[].class);

    assertThat(actual).contains(labJournal);
  }

  @Test
  @Order(7)
  public void getLabJournals_dataObjectDoesNotExist_notFound() {
    given().spec(requestSpecOfDefaultUser).when().get(labJournalURL + "?dataObjectId=99999").then().statusCode(404);
  }

  @Test
  @Order(8)
  public void deleteLabJournal_Success() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(labJournalURL + "/" + labJournal.getId())
      .then()
      .statusCode(204);
  }
}
