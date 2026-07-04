package de.dlr.shepard.v2.references.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V2CONV-A2 — shared Jackson {@link JsonNode} → plain {@code Map}/{@code List}
 * conversion preserving explicit {@code null}s (so RFC 7396 merge-patch
 * explicit-null clears flow through to the per-kind services). Extracted from
 * the {@code jsonNodeToMap} helpers that the per-kind v2 reference resources
 * each carried a copy of, so the unified dispatcher and the surviving per-kind
 * resources share one implementation.
 */
public final class JsonNodeMaps {

  private JsonNodeMaps() {}

  /** Convert a JSON object node to a {@code Map<String,Object>}; non-objects yield an empty map. */
  public static Map<String, Object> toMap(JsonNode node) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (node == null || !node.isObject()) return out;
    var fields = node.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      out.put(e.getKey(), toValue(e.getValue()));
    }
    return out;
  }

  private static Object toValue(JsonNode v) {
    if (v == null || v.isNull()) return null;
    if (v.isTextual()) return v.asText();
    if (v.isInt() || v.isLong()) return v.asLong();
    if (v.isNumber()) return v.asDouble();
    if (v.isBoolean()) return v.asBoolean();
    if (v.isObject()) return toMap(v);
    if (v.isArray()) {
      List<Object> list = new ArrayList<>(v.size());
      for (JsonNode el : v) list.add(toValue(el));
      return list;
    }
    return v.toString();
  }
}
