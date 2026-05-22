package de.dlr.shepard.integrationtests.wirefidelity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Normalises JSON responses for byte-stable comparison against a recorded fixture.
 *
 * <p>Strategy = <strong>schema-strict + value-strict-where-static</strong>:
 *
 * <ul>
 *   <li><strong>Key set</strong> — the recorded fixture's object keys must match the live
 *       response's keys exactly (same set, no extras, no omissions). This is what makes the
 *       suite an upstream wire-compat regression guard.</li>
 *   <li><strong>Static values</strong> — fields whose value is deterministic (anything the test
 *       fixture's request body set: {@code name}, {@code description}, {@code attributes}, …) are
 *       compared by value.</li>
 *   <li><strong>Dynamic values</strong> — fields the server mints at write time (Neo4j long
 *       {@code id}, UUIDv7 {@code appId}, {@code createdAt}/{@code updatedAt} instants,
 *       {@code createdBy} username, transient {@code jws} tokens, server-side hashes, …) are
 *       replaced with sentinel placeholders during normalisation. The fixture stores the same
 *       sentinel. Effectively: "this key MUST exist and have a value of this kind, but the
 *       exact value isn't part of the wire contract".</li>
 * </ul>
 *
 * <p>Sentinels recognised here:
 *
 * <ul>
 *   <li>{@code <<ANY-LONG>>}     — any numeric integer (matches Neo4j-OGM long ids)</li>
 *   <li>{@code <<ANY-STRING>>}   — any non-null string (matches mint UUIDs, instants, usernames)</li>
 *   <li>{@code <<ANY-BOOLEAN>>}  — true or false</li>
 *   <li>{@code <<ANY-LONG[]>>}   — any array of integers (matches id lists in order-irrelevant way)</li>
 *   <li>{@code <<ANY-STRING[]>>} — any array of strings</li>
 *   <li>{@code <<ANY-OBJECT>>}   — any object (used sparingly for opaque server-side blobs)</li>
 *   <li>{@code <<NULL-OR-STRING>>} — null is also acceptable (for nullable mint fields)</li>
 * </ul>
 *
 * <p>Object field ordering is <em>not</em> required to match (JSON objects are unordered by
 * spec); array ordering <em>is</em> required to match (JSON arrays are ordered).
 *
 * <h2>Adding a new fixture</h2>
 *
 * See {@link V5WireFidelityTest} javadoc for the full recipe.
 */
public final class V5JsonNormalizer {

  public static final String ANY_LONG = "<<ANY-LONG>>";
  public static final String ANY_STRING = "<<ANY-STRING>>";
  public static final String ANY_BOOLEAN = "<<ANY-BOOLEAN>>";
  public static final String ANY_LONG_ARRAY = "<<ANY-LONG[]>>";
  public static final String ANY_STRING_ARRAY = "<<ANY-STRING[]>>";
  public static final String ANY_OBJECT = "<<ANY-OBJECT>>";
  public static final String NULL_OR_STRING = "<<NULL-OR-STRING>>";

  private V5JsonNormalizer() {}

  /**
   * Asserts {@code actual} matches {@code expected} under the wire-fidelity comparison rules.
   *
   * @throws WireFidelityMismatchException with a path-qualified description of the first
   *     mismatch encountered
   */
  public static void assertMatches(JsonNode expected, JsonNode actual) {
    List<String> errors = new ArrayList<>();
    compare("$", expected, actual, errors);
    if (!errors.isEmpty()) {
      throw new WireFidelityMismatchException(String.join("\n  ", errors));
    }
  }

  private static void compare(String path, JsonNode expected, JsonNode actual, List<String> errors) {
    if (expected.isTextual() && isSentinel(expected.asText())) {
      checkSentinel(path, expected.asText(), actual, errors);
      return;
    }

    if (expected.getNodeType() != actual.getNodeType() && !(expected.isNumber() && actual.isNumber())) {
      errors.add(path + ": expected " + expected.getNodeType() + " but got " + actual.getNodeType() + " (actual=" + actual + ")");
      return;
    }

    if (expected.isObject()) {
      Set<String> expectedKeys = fieldNames((ObjectNode) expected);
      Set<String> actualKeys = fieldNames((ObjectNode) actual);
      Set<String> onlyInExpected = new TreeSet<>(expectedKeys);
      onlyInExpected.removeAll(actualKeys);
      Set<String> onlyInActual = new TreeSet<>(actualKeys);
      onlyInActual.removeAll(expectedKeys);
      if (!onlyInExpected.isEmpty()) {
        errors.add(path + ": missing keys in response: " + onlyInExpected);
      }
      if (!onlyInActual.isEmpty()) {
        errors.add(path + ": extra keys in response: " + onlyInActual);
      }
      for (String key : expectedKeys) {
        if (actualKeys.contains(key)) {
          compare(path + "." + key, expected.get(key), actual.get(key), errors);
        }
      }
    } else if (expected.isArray()) {
      ArrayNode expArr = (ArrayNode) expected;
      ArrayNode actArr = (ArrayNode) actual;
      if (expArr.size() != actArr.size()) {
        errors.add(path + ": array size mismatch (expected " + expArr.size() + ", got " + actArr.size() + ")");
        return;
      }
      for (int i = 0; i < expArr.size(); i++) {
        compare(path + "[" + i + "]", expArr.get(i), actArr.get(i), errors);
      }
    } else if (expected.isNumber()) {
      // numeric: compare as BigDecimal to honour strict precision per task requirement
      if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
        errors.add(path + ": numeric mismatch (expected " + expected + ", got " + actual + ")");
      }
    } else if (expected.isNull()) {
      if (!actual.isNull()) {
        errors.add(path + ": expected null, got " + actual);
      }
    } else {
      // textual / boolean
      if (!expected.equals(actual)) {
        errors.add(path + ": value mismatch (expected " + expected + ", got " + actual + ")");
      }
    }
  }

  private static boolean isSentinel(String text) {
    return text.startsWith("<<") && text.endsWith(">>");
  }

  private static void checkSentinel(String path, String sentinel, JsonNode actual, List<String> errors) {
    switch (sentinel) {
      case ANY_LONG:
        if (!actual.isIntegralNumber()) {
          errors.add(path + ": expected long/integer, got " + actual);
        }
        break;
      case ANY_STRING:
        if (!actual.isTextual() || actual.asText().isEmpty()) {
          errors.add(path + ": expected non-empty string, got " + actual);
        }
        break;
      case ANY_BOOLEAN:
        if (!actual.isBoolean()) {
          errors.add(path + ": expected boolean, got " + actual);
        }
        break;
      case ANY_LONG_ARRAY:
        if (!actual.isArray()) {
          errors.add(path + ": expected array, got " + actual);
          break;
        }
        for (int i = 0; i < actual.size(); i++) {
          if (!actual.get(i).isIntegralNumber()) {
            errors.add(path + "[" + i + "]: expected integer in array, got " + actual.get(i));
          }
        }
        break;
      case ANY_STRING_ARRAY:
        if (!actual.isArray()) {
          errors.add(path + ": expected array, got " + actual);
          break;
        }
        for (int i = 0; i < actual.size(); i++) {
          if (!actual.get(i).isTextual()) {
            errors.add(path + "[" + i + "]: expected string in array, got " + actual.get(i));
          }
        }
        break;
      case ANY_OBJECT:
        if (!actual.isObject()) {
          errors.add(path + ": expected object, got " + actual);
        }
        break;
      case NULL_OR_STRING:
        if (!actual.isNull() && !actual.isTextual()) {
          errors.add(path + ": expected null or string, got " + actual);
        }
        break;
      default:
        errors.add(path + ": unknown sentinel " + sentinel);
    }
  }

  private static Set<String> fieldNames(ObjectNode node) {
    Set<String> out = new TreeSet<>();
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      out.add(it.next());
    }
    return out;
  }

  /**
   * Normalises a recorded JSON response by overwriting dynamic fields with sentinels.
   *
   * <p>Used by {@link V5WireFidelityTest} when recording a new fixture
   * ({@code -Dshepard.fixtures.record=true}). Operators capturing fixtures by hand can call
   * this on the wire JSON to get the canonical form.
   *
   * @param mapper Jackson mapper for re-serialising
   * @param wire the raw JSON tree as returned by the server
   * @param dynamicFields keys to overwrite. Map value is the sentinel to use.
   * @return new ObjectNode with sentinels substituted
   */
  public static JsonNode redactDynamicFields(
    ObjectMapper mapper,
    JsonNode wire,
    Map<String, String> dynamicFields
  ) {
    return redactRecursive(mapper, wire, dynamicFields);
  }

  private static JsonNode redactRecursive(
    ObjectMapper mapper,
    JsonNode node,
    Map<String, String> dynamicFields
  ) {
    if (node.isObject()) {
      ObjectNode out = mapper.createObjectNode();
      // sort keys for stable diff
      Map<String, JsonNode> entries = new LinkedHashMap<>();
      Iterator<Map.Entry<String, JsonNode>> it = node.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        entries.put(e.getKey(), e.getValue());
      }
      List<String> keys = new ArrayList<>(entries.keySet());
      keys.sort(String::compareTo);
      for (String key : keys) {
        JsonNode value = entries.get(key);
        if (dynamicFields.containsKey(key)) {
          out.put(key, dynamicFields.get(key));
        } else {
          out.set(key, redactRecursive(mapper, value, dynamicFields));
        }
      }
      return out;
    } else if (node.isArray()) {
      ArrayNode out = mapper.createArrayNode();
      for (JsonNode child : node) {
        out.add(redactRecursive(mapper, child, dynamicFields));
      }
      return out;
    } else {
      return node;
    }
  }
}
