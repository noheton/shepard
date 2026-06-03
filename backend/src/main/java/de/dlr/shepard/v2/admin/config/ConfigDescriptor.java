package de.dlr.shepard.v2.admin.config;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * V2CONV-A4 — SPI for registry-driven admin config endpoints.
 *
 * <p>Each implementation calls {@link ConfigRegistry#register(ConfigDescriptor)}
 * (typically from its {@code @Observes StartupEvent} method) so the generic
 * {@code GET|PATCH /v2/admin/config/{feature}} endpoint can dispatch by name.
 */
public interface ConfigDescriptor {

  /** Feature name as it appears in the URL path segment (e.g. {@code "sql-timeseries"}). */
  String featureName();

  /** Returns the current config snapshot as a JSON-serializable object. */
  Object read();

  /**
   * RFC 7396 merge-patch: absent fields → leave alone, null fields → clear to
   * deploy-time default, non-null fields → replace current value.
   *
   * @param patch JSON node from the request body (null = no-op empty patch)
   * @return updated config snapshot (same shape as {@link #read()})
   * @throws ConfigValidationException on invalid field values (→ 400)
   */
  Object patch(JsonNode patch) throws ConfigValidationException;
}
