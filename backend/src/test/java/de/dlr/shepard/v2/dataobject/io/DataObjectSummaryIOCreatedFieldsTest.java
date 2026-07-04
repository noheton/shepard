package de.dlr.shepard.v2.dataobject.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * PRED-V2-SHAPE — regression guard: {@link DataObjectSummaryIO} must include
 * {@code createdAt} (ISO-8601 string) and {@code createdBy} (display name)
 * so the Predecessor / Successor panel can sort and credit entries without
 * falling back to the v1 {@code getAllDataObjects} endpoint.
 */
class DataObjectSummaryIOCreatedFieldsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper()
    .findAndRegisterModules()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void wireShape_includes_id_whenSet() throws Exception {
    DataObject d = new DataObject();
    d.setId(42L);
    d.setAppId("018f-pred-00");
    d.setName("TR-004-id-test");

    var io = new DataObjectSummaryIO(d);
    JsonNode json = MAPPER.valueToTree(io);

    assertThat(json.has("id")).isTrue();
    assertThat(json.get("id").asLong()).isEqualTo(42L);
  }

  @Test
  void wireShape_includes_createdAt_whenSet() throws Exception {
    DataObject d = new DataObject();
    d.setAppId("018f-pred-01");
    d.setName("TR-004-predecessor");
    d.setStatus("READY");
    Date ts = new Date(1718000000000L);
    d.setCreatedAt(ts);
    // no createdBy → null

    var io = new DataObjectSummaryIO(d);
    JsonNode json = MAPPER.valueToTree(io);

    assertThat(json.has("createdAt")).isTrue();
    // ISO-8601 string — must not be a bare numeric timestamp
    assertThat(json.get("createdAt").isTextual()).isTrue();
    assertThat(json.get("appId").asText()).isEqualTo("018f-pred-01");
    assertThat(json.get("name").asText()).isEqualTo("TR-004-predecessor");
    assertThat(json.get("status").asText()).isEqualTo("READY");
  }

  @Test
  void wireShape_includes_createdBy_whenUserSet() throws Exception {
    User user = new User();
    user.setUsername("flo");
    user.setFirstName("Flo");
    user.setLastName("Krebs");

    DataObject d = new DataObject();
    d.setAppId("018f-pred-02");
    d.setName("TR-005");
    d.setCreatedBy(user);

    var io = new DataObjectSummaryIO(d);
    JsonNode json = MAPPER.valueToTree(io);

    assertThat(json.has("createdBy")).isTrue();
    assertThat(json.get("createdBy").asText()).isEqualTo("Flo Krebs");
  }

  @Test
  void wireShape_omits_createdBy_whenNull() throws Exception {
    DataObject d = new DataObject();
    d.setAppId("018f-pred-03");
    d.setName("TR-006");
    // createdBy not set

    var io = new DataObjectSummaryIO(d);
    JsonNode json = MAPPER.valueToTree(io);

    assertThat(json.has("createdBy")).isFalse();
  }

  @Test
  void wireShape_omits_createdAt_whenNull() throws Exception {
    DataObject d = new DataObject();
    d.setAppId("018f-pred-04");
    d.setName("TR-007");
    // createdAt not set

    var io = new DataObjectSummaryIO(d);
    JsonNode json = MAPPER.valueToTree(io);

    assertThat(json.has("createdAt")).isFalse();
  }
}
