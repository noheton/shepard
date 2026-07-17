package de.dlr.shepard.v2.labjournal.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-LABJOURNAL-NUMID-DATE — wire-shape guard.
 *
 * <p>Verifies that {@link LabJournalEntryV2IO}:
 * <ul>
 *   <li>has no {@code dataObjectId} (numeric Neo4j id) on the v2 wire</li>
 *   <li>has no numeric {@code id} on the v2 wire</li>
 *   <li>serialises {@code createdAt}/{@code updatedAt} as ISO-8601 strings, not epoch numbers</li>
 *   <li>preserves {@code appId}, {@code dataObjectAppId}, {@code journalContent}, {@code contentFormat}</li>
 * </ul>
 */
class LabJournalEntryV2IOTest {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private static final Date FIXED_DATE = new Date(1_718_000_000_000L);

  private LabJournalEntry buildEntry() {
    User creator = new User();
    creator.setUsername("flo");
    creator.setFirstName("Flo");
    creator.setLastName("Krebs");

    DataObject dataObject = new DataObject(99L);
    dataObject.setAppId("do-app-uuid-1");

    LabJournalEntry entry = new LabJournalEntry();
    entry.setId(42L);
    entry.setAppId("lj-app-uuid-1");
    entry.setContent("Test entry content.");
    entry.setDataObject(dataObject);
    entry.setCreatedAt(FIXED_DATE);
    entry.setCreatedBy(creator);
    entry.setUpdatedAt(new Date(FIXED_DATE.getTime() + 60_000));
    entry.setUpdatedBy(creator);
    return entry;
  }

  @Test
  void wireShape_excludes_numericDataObjectId() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));
    assertThat(json.has("dataObjectId"))
        .as("dataObjectId (numeric Neo4j id) must not appear on the v2 wire")
        .isFalse();
  }

  @Test
  void wireShape_excludes_numericId() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));
    assertThat(json.has("id"))
        .as("numeric Neo4j id must not appear on the v2 wire")
        .isFalse();
  }

  @Test
  void wireShape_includesAppId_and_dataObjectAppId() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));
    assertThat(json.get("appId").asText()).isEqualTo("lj-app-uuid-1");
    assertThat(json.get("dataObjectAppId").asText()).isEqualTo("do-app-uuid-1");
  }

  @Test
  void wireShape_timestamps_areISO8601Strings() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));

    JsonNode createdAt = json.get("createdAt");
    assertThat(createdAt).isNotNull();
    assertThat(createdAt.isTextual())
        .as("createdAt must serialise as an ISO-8601 string, not an epoch number")
        .isTrue();
    assertThat(createdAt.asText()).contains("2024");

    JsonNode updatedAt = json.get("updatedAt");
    assertThat(updatedAt).isNotNull();
    assertThat(updatedAt.isTextual())
        .as("updatedAt must serialise as an ISO-8601 string, not an epoch number")
        .isTrue();
  }

  @Test
  void wireShape_includesContentFormat_MARKDOWN() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));
    assertThat(json.get("contentFormat").asText()).isEqualTo("MARKDOWN");
  }

  @Test
  void wireShape_includesJournalContent() throws Exception {
    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(buildEntry()));
    assertThat(json.get("journalContent").asText()).isEqualTo("Test entry content.");
  }

  @Test
  void wireShape_nullableFieldsAbsentWhenNull() throws Exception {
    LabJournalEntry entry = new LabJournalEntry();
    entry.setAppId("lj-null-test");
    entry.setContent("content");
    // no dataObject, no createdAt, no updatedAt, no createdBy, no updatedBy

    JsonNode json = MAPPER.valueToTree(new LabJournalEntryV2IO(entry));
    assertThat(json.has("dataObjectAppId")).isFalse();
    assertThat(json.has("createdAt")).isFalse();
    assertThat(json.has("updatedAt")).isFalse();
  }

  @Test
  void noArgConstructor_yieldsNullFields() {
    var io = new LabJournalEntryV2IO();
    assertThat(io.getAppId()).isNull();
    assertThat(io.getDataObjectAppId()).isNull();
    assertThat(io.getCreatedAt()).isNull();
    assertThat(io.getContentFormat()).isEqualTo("MARKDOWN");
  }
}
