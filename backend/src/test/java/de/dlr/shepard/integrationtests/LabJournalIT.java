package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.labJournal.io.LabJournalEntryIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import org.junit.jupiter.api.BeforeAll;
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

    assertThat(actual.getMessage()).isEqualTo("LabJournal with Id 99999 cannot be found or is deleted");
  }

  @Test
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

  // -- P21: RFC 7396 JSON Merge Patch on /labJournalEntries/{id} -------------
  // See aidocs/16-dispatcher-backlog.md P21; aidocs/26-crud-consistency.md
  // finding #1. PATCH ships additively in /v1/ alongside the existing PUT
  // (which keeps full-replace semantics). Mirrors the P21 Collection pilot.

  private static LabJournalEntryIO seedLabJournal(String marker) {
    LabJournalEntryIO payload = new LabJournalEntryIO();
    payload.setJournalContent("<p>" + marker + "</p>");
    return given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .queryParam("dataObjectId", dataObject.getId())
      .when()
      .post(labJournalURL)
      .then()
      .statusCode(201)
      .extract()
      .as(LabJournalEntryIO.class);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_happyPath_updatesJournalContent() {
    // Arrange: create a fresh lab journal so other tests are unaffected.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalHappy" + System.currentTimeMillis());

    // Act: PATCH only journalContent.
    String mergePatch = "{\"journalContent\":\"<p>new</p>\"}";
    LabJournalEntryIO patched = given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(LabJournalEntryIO.class);

    // Assert: journalContent changed; everything else preserved.
    assertThat(patched.getJournalContent()).isEqualTo("<p>new</p>");
    assertThat(patched.getId()).isEqualTo(created.getId());
    assertThat(patched.getDataObjectId()).isEqualTo(created.getDataObjectId());
    assertThat(patched.getCreatedBy()).isEqualTo(created.getCreatedBy());
    assertThat(patched.getUpdatedAt()).isNotNull();
    assertThat(patched.getUpdatedBy()).isEqualTo(nameOfDefaultUser);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_explicitNullOnNotNullField_returns400() {
    // Every writable field on LabJournalEntryIO is @NotNull, so per the P21
    // template this case asserts that explicit JSON null on a @NotNull field
    // (journalContent) fails Bean Validation against the merged result -> 400.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalNull" + System.currentTimeMillis());

    String mergePatch = "{\"journalContent\":null}";
    given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(400);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_emptyBody_isNoOp() {
    // Arrange.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalNoOp" + System.currentTimeMillis());

    // Act: empty merge patch.
    LabJournalEntryIO patched = given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body("{}")
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(LabJournalEntryIO.class);

    // Assert: every domain field preserved (updatedAt/By legitimately change).
    assertThat(patched)
      .usingRecursiveComparison()
      .ignoringFields("updatedBy", "updatedAt")
      .isEqualTo(created);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_mergeViolatesValidation_returns400() {
    // Arrange.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalValidation" + System.currentTimeMillis());

    // Act: dataObjectId is @NotNull on the merged result; clearing it must fail
    // validation on the merged state -> 400.
    String mergePatch = "{\"dataObjectId\":null}";
    given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(400);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_withoutWritePermission_returns403() {
    // Arrange: defaultUser is the creator; otherUser is not the creator.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalPerm" + System.currentTimeMillis());

    // Act + Assert: otherUser cannot PATCH (creator-only check matches PUT).
    given()
      .spec(requestSpecOfOtherUser)
      .contentType("application/merge-patch+json")
      .body("{\"journalContent\":\"<p>hacked</p>\"}")
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(403);

    // And without auth at all -> 401.
    var noAuth = new RequestSpecBuilder().setContentType("application/merge-patch+json").build();
    given()
      .spec(noAuth)
      .body("{\"journalContent\":\"<p>hacked</p>\"}")
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(401);
  }

  @Test
  @Order(9)
  public void patchLabJournalTest_acceptsApplicationJsonContentType() {
    // Pilot decision: the /v1/ PATCH accepts both application/merge-patch+json
    // (RFC 7396 preferred) and application/json so existing callers and SDK
    // generators that don't yet emit the merge-patch media type aren't broken.
    // Future /v2/ will require application/merge-patch+json.
    LabJournalEntryIO created = seedLabJournal("PatchLabJournalCT" + System.currentTimeMillis());

    LabJournalEntryIO patched = given()
      .spec(requestSpecOfDefaultUser) // ContentType.JSON ("application/json")
      .body("{\"journalContent\":\"<p>via-json</p>\"}")
      .when()
      .patch(labJournalURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(LabJournalEntryIO.class);

    assertThat(patched.getJournalContent()).isEqualTo("<p>via-json</p>");
    assertThat(patched.getId()).isEqualTo(created.getId());
  }
}
