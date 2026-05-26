package de.dlr.shepard.provenance.entities;

import java.util.Set;

/**
 * Canonical set of values for {@link Activity#getActionKind()}.
 *
 * <p>Neo4j Community Edition does not support value-existence constraints
 * (that's an Enterprise-only feature), so this enum serves as the app-layer
 * enforcement point. Any code that writes an {@link Activity} row must
 * pass one of these values; {@link #validate(String)} throws
 * {@link IllegalArgumentException} for anything outside the set.
 *
 * <p>The five canonical values follow CRUD + an escape hatch:
 * <ul>
 *   <li>{@code CREATE} — a new entity was created (HTTP POST → 2xx)</li>
 *   <li>{@code READ}   — an entity was read (HTTP GET/HEAD, opt-in capture)</li>
 *   <li>{@code UPDATE} — an entity was mutated (HTTP PUT/PATCH → 2xx)</li>
 *   <li>{@code DELETE} — an entity was removed (HTTP DELETE → 2xx)</li>
 *   <li>{@code EXECUTE} — a non-CRUD action: sTC ingest tick, export run,
 *       future coordinator step; HTTP verb doesn't map cleanly to the above</li>
 * </ul>
 *
 * <p>Note: {@code AI_ACTION} is intentionally NOT in this enum — it is a TPL9
 * extension value used by the f(ai)²r AI-provenance capture path
 * ({@code AiProvenanceCapture}, {@code aidocs/platform/89}). It is not routed
 * through {@link de.dlr.shepard.provenance.services.ProvenanceService#record}
 * and therefore not subject to validation here. The startup alarm in
 * {@link ActivityActionKindAlarm} includes it in the allowed set so that
 * AI_ACTION rows already in the database do not trigger a false-positive warn.
 *
 * @see de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter#actionKindFor(String)
 * @see de.dlr.shepard.provenance.services.ActivityActionKindAlarm
 */
public enum ActivityActionKind {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  EXECUTE;

  /**
   * All values recognised by this instance — includes the TPL9 {@code AI_ACTION}
   * extension so the startup alarm does not cry wolf on rows written by
   * {@code AiProvenanceCapture}.
   */
  public static final Set<String> ALL_KNOWN_VALUES = Set.of(
    "CREATE", "READ", "UPDATE", "DELETE", "EXECUTE", "AI_ACTION"
  );

  /**
   * Validates that {@code value} is one of the known actionKind strings.
   *
   * <p>Accepts all five enum values ({@code CREATE}, {@code READ}, {@code UPDATE},
   * {@code DELETE}, {@code EXECUTE}) plus the TPL9 extension value
   * {@code AI_ACTION} which is written by {@code AiProvenanceCapture} and is
   * part of {@link #ALL_KNOWN_VALUES}.
   *
   * @param value the string to validate
   * @throws IllegalArgumentException if {@code value} is null or not in
   *         {@link #ALL_KNOWN_VALUES}
   */
  public static void validate(String value) {
    if (value == null || !ALL_KNOWN_VALUES.contains(value)) {
      throw new IllegalArgumentException(
        "Activity.actionKind '" + value + "' is not a canonical value; valid values: " + ALL_KNOWN_VALUES);
    }
  }
}
