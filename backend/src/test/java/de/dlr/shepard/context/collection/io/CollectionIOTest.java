package de.dlr.shepard.context.collection.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class CollectionIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(CollectionIO.class).withIgnoredFields("revision").verify();
  }

  /**
   * Regression for task #131 — {@code heroImageUrl} is a fork addition
   * with no upstream 5.2.0 counterpart. Per {@code CLAUDE.md §"API-version
   * policy"} the {@code /shepard/api/...} surface must stay byte-compatible
   * with upstream, so the field must be omitted from JSON when null — not
   * serialised as {@code "heroImageUrl": null}. The
   * {@code @JsonInclude(NON_NULL)} on the field is the fix; this test
   * pins that contract at the serialisation boundary so a future
   * refactor that drops the annotation fails fast (and not just in the
   * fixture-corpus IT that needs Keycloak to run).
   */
  @Test
  public void heroImageUrl_isOmittedFromJson_whenNull() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    // heroImageUrl left null (the default)
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("heroImageUrl");
  }

  /** When set, the field MUST round-trip — NON_NULL only suppresses null. */
  @Test
  public void heroImageUrl_isSerialised_whenSet() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    io.setHeroImageUrl("https://example.com/banner.png");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"heroImageUrl\":\"https://example.com/banner.png\"");
  }

  // ── LIC1 (FAIR-1): license + accessRights wire contract ────────────────
  //
  // Per CLAUDE.md §"API-version policy" the /shepard/api/... surface must stay
  // byte-compatible with upstream shepard 5.2.0, which has NEITHER of these
  // fields. The @JsonInclude(NON_NULL) annotations on `license` and `accessRights`
  // (in AbstractDataObjectIO) keep them off the wire when unset, so an
  // out-of-the-box Collection serialises with the upstream-identical key set.

  @Test
  public void license_isOmittedFromJson_whenNull() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("license");
  }

  @Test
  public void license_isSerialised_whenSet() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    io.setLicense("CC-BY-4.0");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"license\":\"CC-BY-4.0\"");
  }

  @Test
  public void license_roundTripsThroughJson() throws Exception {
    var mapper = new ObjectMapper();
    var io = new CollectionIO();
    io.setName("test");
    io.setLicense("Apache-2.0");
    String json = mapper.writeValueAsString(io);
    var deserialised = mapper.readValue(json, CollectionIO.class);
    assertEquals("Apache-2.0", deserialised.getLicense());
  }

  @Test
  public void accessRights_isOmittedFromJson_whenNull() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("accessRights");
  }

  @Test
  public void accessRights_isSerialised_whenSet() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    io.setAccessRights("RESTRICTED");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"accessRights\":\"RESTRICTED\"");
  }

  @Test
  public void accessRights_roundTripsThroughJson() throws Exception {
    var mapper = new ObjectMapper();
    var io = new CollectionIO();
    io.setName("test");
    io.setAccessRights("EMBARGOED");
    String json = mapper.writeValueAsString(io);
    var deserialised = mapper.readValue(json, CollectionIO.class);
    assertEquals("EMBARGOED", deserialised.getAccessRights());
  }

  @Test
  public void licenseAndAccessRights_serialiseTogether() throws Exception {
    var io = new CollectionIO();
    io.setName("test");
    io.setLicense("CC0-1.0");
    io.setAccessRights("OPEN");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"license\":\"CC0-1.0\"");
    assertThat(json).contains("\"accessRights\":\"OPEN\"");
  }

  @Test
  public void testConversion() {
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(4L);
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new Collection(1L);
    obj.setShepardId(2L);
    obj.setAttributes(Map.of("a", "b", "c", "1"));
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setDataObjects(List.of(dataObject));
    obj.setDescription("My Description");
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setLicense("CC-BY-4.0");
    obj.setAccessRights("RESTRICTED");

    var converted = new CollectionIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getAttributes(), converted.getAttributes());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals("[4]", Arrays.toString(converted.getDataObjectIds()));
    assertEquals(obj.getDescription(), converted.getDescription());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals("CC-BY-4.0", converted.getLicense());
    assertEquals("RESTRICTED", converted.getAccessRights());
  }
}
