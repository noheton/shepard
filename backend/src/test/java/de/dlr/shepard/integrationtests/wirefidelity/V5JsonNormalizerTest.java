package de.dlr.shepard.integrationtests.wirefidelity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link V5JsonNormalizer} — the matching engine that backs every
 * {@link V5WireFidelityTest} subclass. Lives under {@code src/test/java} alongside the
 * integration suite but is itself a plain JUnit @Test (runs under surefire).
 *
 * <p>Covers: sentinel recognition for each placeholder kind, strict key-set comparison
 * (extras + missing both fail), nested objects, arrays, numeric precision, and
 * dynamic-field redaction.
 */
class V5JsonNormalizerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void matchesExactJson() throws Exception {
    JsonNode expected = mapper.readTree("{\"name\":\"foo\",\"count\":3}");
    JsonNode actual = mapper.readTree("{\"name\":\"foo\",\"count\":3}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void matchesAnyLongSentinel() throws Exception {
    JsonNode expected = mapper.readTree("{\"id\":\"<<ANY-LONG>>\"}");
    JsonNode actual = mapper.readTree("{\"id\":42}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void rejectsAnyLongSentinelWhenStringPresent() throws Exception {
    JsonNode expected = mapper.readTree("{\"id\":\"<<ANY-LONG>>\"}");
    JsonNode actual = mapper.readTree("{\"id\":\"oops\"}");
    assertThatThrownBy(() -> V5JsonNormalizer.assertMatches(expected, actual))
      .isInstanceOf(WireFidelityMismatchException.class)
      .hasMessageContaining("expected long");
  }

  @Test
  void rejectsExtraKeyInActual() throws Exception {
    JsonNode expected = mapper.readTree("{\"a\":1}");
    JsonNode actual = mapper.readTree("{\"a\":1,\"b\":2}");
    assertThatThrownBy(() -> V5JsonNormalizer.assertMatches(expected, actual))
      .isInstanceOf(WireFidelityMismatchException.class)
      .hasMessageContaining("extra keys");
  }

  @Test
  void rejectsMissingKeyInActual() throws Exception {
    JsonNode expected = mapper.readTree("{\"a\":1,\"b\":2}");
    JsonNode actual = mapper.readTree("{\"a\":1}");
    assertThatThrownBy(() -> V5JsonNormalizer.assertMatches(expected, actual))
      .isInstanceOf(WireFidelityMismatchException.class)
      .hasMessageContaining("missing keys");
  }

  @Test
  void rejectsArraySizeMismatch() throws Exception {
    JsonNode expected = mapper.readTree("{\"xs\":[1,2,3]}");
    JsonNode actual = mapper.readTree("{\"xs\":[1,2]}");
    assertThatThrownBy(() -> V5JsonNormalizer.assertMatches(expected, actual))
      .isInstanceOf(WireFidelityMismatchException.class)
      .hasMessageContaining("array size mismatch");
  }

  @Test
  void acceptsLongArraySentinel() throws Exception {
    JsonNode expected = mapper.readTree("{\"ids\":\"<<ANY-LONG[]>>\"}");
    JsonNode actual = mapper.readTree("{\"ids\":[1,2,3]}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void acceptsStringArraySentinel() throws Exception {
    JsonNode expected = mapper.readTree("{\"names\":\"<<ANY-STRING[]>>\"}");
    JsonNode actual = mapper.readTree("{\"names\":[\"a\",\"b\"]}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void acceptsNullOrStringSentinelForNull() throws Exception {
    JsonNode expected = mapper.readTree("{\"updatedAt\":\"<<NULL-OR-STRING>>\"}");
    JsonNode actual = mapper.readTree("{\"updatedAt\":null}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void acceptsNullOrStringSentinelForString() throws Exception {
    JsonNode expected = mapper.readTree("{\"updatedAt\":\"<<NULL-OR-STRING>>\"}");
    JsonNode actual = mapper.readTree("{\"updatedAt\":\"2024-08-15T11:18:44.632+00:00\"}");
    V5JsonNormalizer.assertMatches(expected, actual);
  }

  @Test
  void rejectsNumericValueMismatch() throws Exception {
    JsonNode expected = mapper.readTree("{\"revision\":1}");
    JsonNode actual = mapper.readTree("{\"revision\":2}");
    assertThatThrownBy(() -> V5JsonNormalizer.assertMatches(expected, actual))
      .isInstanceOf(WireFidelityMismatchException.class)
      .hasMessageContaining("numeric mismatch");
  }

  @Test
  void redactsDynamicFields() throws Exception {
    JsonNode wire = mapper.readTree(
      "{\"id\":42,\"name\":\"x\",\"appId\":\"01H7-…\",\"createdAt\":\"2024-01-01T00:00:00Z\"}"
    );
    Map<String, String> dyn = Map.of(
      "id", V5JsonNormalizer.ANY_LONG,
      "appId", V5JsonNormalizer.ANY_STRING,
      "createdAt", V5JsonNormalizer.ANY_STRING
    );
    JsonNode redacted = V5JsonNormalizer.redactDynamicFields(mapper, wire, dyn);
    assertThat(redacted.get("id").asText()).isEqualTo(V5JsonNormalizer.ANY_LONG);
    assertThat(redacted.get("appId").asText()).isEqualTo(V5JsonNormalizer.ANY_STRING);
    assertThat(redacted.get("createdAt").asText()).isEqualTo(V5JsonNormalizer.ANY_STRING);
    // name is not dynamic; left intact
    assertThat(redacted.get("name").asText()).isEqualTo("x");
  }
}
