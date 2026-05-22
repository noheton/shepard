package de.dlr.shepard.context.collection.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class DataObjectIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(DataObjectIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var incoming = new DataObjectReference(7L);
    incoming.setShepardId(44L);
    var parent = new DataObject(2L);
    parent.setShepardId(498L);
    var child = new DataObject(3L);
    child.setShepardId(4090L);
    var suc = new DataObject(4L);
    suc.setShepardId(96L);
    var pre = new DataObject(5L);
    pre.setShepardId(4748L);
    var col = new Collection(6L);
    col.setShepardId(366L);

    var obj = new DataObject(1L);
    obj.setShepardId(98765L);
    obj.setAttributes(Map.of("a", "b", "c", "1"));
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setDescription("My Description");
    obj.setIncoming(List.of(incoming));
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setParent(parent);
    obj.setChildren(List.of(child));
    obj.setPredecessors(List.of(pre));
    obj.setSuccessors(List.of(suc));
    obj.setCollection(col);

    var converted = new DataObjectIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getAttributes(), converted.getAttributes());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getDescription(), converted.getDescription());
    assertEquals("[" + obj.getIncoming().getFirst().getShepardId() + "]", Arrays.toString(converted.getIncomingIds()));
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(parent.getShepardId(), converted.getParentId());
    assertEquals(col.getShepardId(), converted.getCollectionId());
    assertEquals("[" + obj.getChildren().getFirst().getShepardId() + "]", Arrays.toString(converted.getChildrenIds()));
    assertEquals(
      "[" + obj.getSuccessors().getFirst().getShepardId() + "]",
      Arrays.toString(converted.getSuccessorIds())
    );
    assertEquals(
      "[" + obj.getPredecessors().getFirst().getShepardId() + "]",
      Arrays.toString(converted.getPredecessorIds())
    );
  }

  @Test
  public void testConversionNoParent() {
    var col = new Collection(2L);
    col.setShepardId(432L);
    var obj = new DataObject(1L);
    obj.setShepardId(38383L);
    obj.setCollection(col);

    var converted = new DataObjectIO(obj);
    assertNull(converted.getParentId());
  }

  // ── LIC1 (FAIR-1): license + accessRights wire contract ──────────────
  //
  // Same wire-fidelity rationale as CollectionIOTest: these fields are fork
  // additions absent from upstream v5.2.0. They must be omitted from the JSON
  // body when null and round-trip when set.

  @Test
  public void license_isOmittedFromJson_whenNull() throws Exception {
    var io = new DataObjectIO();
    io.setName("do");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("license");
  }

  @Test
  public void accessRights_isOmittedFromJson_whenNull() throws Exception {
    var io = new DataObjectIO();
    io.setName("do");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("accessRights");
  }

  @Test
  public void license_isSerialised_whenSet() throws Exception {
    var io = new DataObjectIO();
    io.setName("do");
    io.setLicense("MIT");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"license\":\"MIT\"");
  }

  @Test
  public void accessRights_isSerialised_whenSet() throws Exception {
    var io = new DataObjectIO();
    io.setName("do");
    io.setAccessRights("CLOSED");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"accessRights\":\"CLOSED\"");
  }

  @Test
  public void licenseAndAccessRights_roundTripThroughJson() throws Exception {
    var mapper = new ObjectMapper();
    var io = new DataObjectIO();
    io.setName("do");
    io.setLicense("ODbL-1.0");
    io.setAccessRights("EMBARGOED");
    String json = mapper.writeValueAsString(io);
    var deserialised = mapper.readValue(json, DataObjectIO.class);
    assertEquals("ODbL-1.0", deserialised.getLicense());
    assertEquals("EMBARGOED", deserialised.getAccessRights());
  }

  @Test
  public void conversion_carriesLicenseAndAccessRightsFromEntity() {
    var col = new Collection(2L);
    col.setShepardId(432L);
    var obj = new DataObject(1L);
    obj.setShepardId(99L);
    obj.setCollection(col);
    obj.setLicense("Apache-2.0");
    obj.setAccessRights("OPEN");

    var converted = new DataObjectIO(obj);
    assertEquals("Apache-2.0", converted.getLicense());
    assertEquals("OPEN", converted.getAccessRights());
  }
}
