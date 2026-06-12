package de.dlr.shepard.v2.collection.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ContainerSummaryIOIdAbsentTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void noGetIdMethodExists() {
    var methods = Arrays.stream(ContainerSummaryIO.class.getMethods())
      .map(Method::getName)
      .toList();
    assertThat(methods).doesNotContain("getId");
  }

  @Test
  void wireShapeHasNoIdField() throws Exception {
    var io = new ContainerSummaryIO("abc-123", "MyContainer", "TIMESERIES");
    JsonNode json = mapper.valueToTree(io);
    assertThat(json.has("id")).isFalse();
  }

  @Test
  void wireShapePreservesAppIdAndName() throws Exception {
    var io = new ContainerSummaryIO("app-uuid", "HotfireTS", "FILE");
    JsonNode json = mapper.valueToTree(io);
    assertThat(json.get("appId").asText()).isEqualTo("app-uuid");
    assertThat(json.get("name").asText()).isEqualTo("HotfireTS");
    assertThat(json.get("containerType").asText()).isEqualTo("FILE");
  }

  @Test
  void noArgConstructorDefaultsNullFields() {
    var io = new ContainerSummaryIO();
    assertThat(io.getAppId()).isNull();
    assertThat(io.getName()).isNull();
    assertThat(io.getContainerType()).isNull();
  }
}
