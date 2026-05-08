package de.dlr.shepard.common.exceptions;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807 {@code application/problem+json} response body.
 *
 * <p>Per RFC 7807 §3.1 the standard fields are:
 * <ul>
 *   <li>{@code type} — URI identifying the problem type.</li>
 *   <li>{@code title} — short, human-readable summary (stable across
 *       occurrences of this type).</li>
 *   <li>{@code status} — HTTP status code.</li>
 *   <li>{@code detail} — human-readable explanation specific to this
 *       occurrence (nullable).</li>
 *   <li>{@code instance} — URI/string identifying the specific occurrence;
 *       we use the request id (nullable).</li>
 * </ul>
 *
 * <p>{@link #extensions} carries per-code extras (e.g. {@code retryAfter},
 * {@code field}, {@code traceId}). It serialises flat alongside the standard
 * fields via {@link JsonAnyGetter}, per RFC 7807 §3.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "title", "status", "detail", "instance" })
public record ProblemJson(
  String type,
  String title,
  int status,
  String detail,
  String instance,
  Map<String, Object> extensions
) {
  public ProblemJson {
    extensions = extensions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
  }

  /** Convenience constructor without extensions. */
  public ProblemJson(String type, String title, int status, String detail, String instance) {
    this(type, title, status, detail, instance, Map.of());
  }

  /**
   * Jackson serialises the keys of this map as flat top-level fields on the
   * problem object — the RFC 7807 §3.2 extension-member shape.
   */
  @JsonAnyGetter
  public Map<String, Object> getExtensions() {
    return extensions;
  }
}
