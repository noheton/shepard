package de.dlr.shepard.context.semantic.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI1v Phase 1 — deterministic suffix / prefix / welding-cap matcher that
 * maps a Timeseries channel {@code field} name to a QUDT unit IRI.
 *
 * <p>This is the rule-based floor of the channel-unit-inference pipeline. It
 * covers ~95/113 of the MFFD + LUMEN demo channels (those whose field name
 * carries a deterministic unit hint such as {@code _mm}, {@code _N},
 * {@code _bar}, or a recognised prefix such as {@code tc_} or {@code pc_}).
 * For the ambiguous tail (e.g. {@code BridgePosition}, {@code valve_fuel},
 * welding controller {@code *_p} / {@code *_t}) the service returns
 * {@link Tier#AMBIGUOUS} and the caller is expected to either skip the
 * annotation or escalate to the LLM-backed Phase 2 (gated on
 * <em>AI1v-Phase-2</em>, depends on the AI plugin / AI1a).
 *
 * <h2>Why a static map, not a vocabulary?</h2>
 * <p>The suffix table is operator code, not a controlled vocabulary — its
 * authority is "the LUMEN/MFFD field-naming convention used in seed.py" not
 * "QUDT". We deliberately do not load it via the
 * {@code SemanticVocabularyProvider} SPI; vocabularies are for what a unit
 * <em>is</em>, this map is for what a field name <em>tends to mean</em>.
 *
 * <h2>Provenance</h2>
 * <p>The dispatch contract specifies {@code sourceMode="ai"} on the
 * generated {@link de.dlr.shepard.context.semantic.entities.SemanticAnnotation}
 * even though no LLM is invoked. The rationale: the inference is automated
 * and unconfirmed by a human, which is exactly the property the EU AI Act
 * Article 50 disclosure cares about. When a human accepts or overrides the
 * annotation via the UI, the {@code sourceMode} flips to
 * {@code "collaborative"} (handled by AI1v-Phase-3 / UI dispatch).
 *
 * <h2>Reference impl</h2>
 * <p>The Python equivalent lives at
 * {@code examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py}.
 * Its {@code SUFFIX_TO_QUDT}, {@code PREFIX_TO_QUDT} and {@code WELDING_CAP_TAIL}
 * dictionaries are ported verbatim here; any divergence is a bug.
 *
 * @see de.dlr.shepard.common.util.Constants#TS_UNIT_PREDICATE
 * @see TimeseriesSemanticDualWriteService (where this service is consumed
 *      on the channel-creation write path)
 */
@ApplicationScoped
public class ChannelUnitInferenceService {

  /** QUDT canonical IRI prefix — verified against QUDT v3.0.x at design time. */
  public static final String QUDT_UNIT_PREFIX = "http://qudt.org/vocab/unit/";

  // ── SUFFIX_TO_QUDT — ported verbatim from the Python recovery script ─────
  // Insertion order matters: longer suffixes MUST come before shorter ones
  // (e.g. "_mm_s" before "_mm", "_Nm" before "_N") so the iteration in
  // infer() does not return a wrong shorter match.
  private static final Map<String, UnitEntry> SUFFIX_TO_QUDT = Map.ofEntries(
    Map.entry("_mm_s", new UnitEntry("MilliM-PER-SEC", "millimeter per second")),
    Map.entry("_mm",   new UnitEntry("MilliM",         "millimeter")),
    Map.entry("_um",   new UnitEntry("MicroM",         "micrometer")),
    Map.entry("_Nm",   new UnitEntry("N-M",            "newton metre")),
    Map.entry("_kN",   new UnitEntry("KiloN",          "kilonewton")),
    Map.entry("_kn",   new UnitEntry("KiloN",          "kilonewton")),
    Map.entry("_N",    new UnitEntry("N",              "newton")),
    Map.entry("_J",    new UnitEntry("J",              "joule")),
    Map.entry("_K",    new UnitEntry("K",              "kelvin")),
    Map.entry("_C",    new UnitEntry("DEG_C",          "degree Celsius")),
    Map.entry("_degC", new UnitEntry("DEG_C",          "degree Celsius")),
    Map.entry("_deg",  new UnitEntry("DEG",            "degree")),
    Map.entry("_bar",  new UnitEntry("BAR",            "bar")),
    Map.entry("_psi",  new UnitEntry("PSI",            "pound-force per square inch")),
    Map.entry("_g",    new UnitEntry("G",              "g-force")),
    Map.entry("_Pa",   new UnitEntry("PA",             "pascal"))
  );

  // PREFIX_TO_QUDT — also ported verbatim. LinkedHashMap to preserve iteration
  // order; the multi-character keys (e.g. "lch4_temperature") MUST come before
  // the short joint-angle keys ("j1_" ..) so that a hypothetical
  // "j1_lch4_temperature" still wins the prefix race deterministically. The
  // current MFFD/LUMEN naming convention does not collide on prefixes, but we
  // preserve the Python script's ordering to keep behaviour identical.
  private static final Map<String, UnitEntry> PREFIX_TO_QUDT;
  static {
    LinkedHashMap<String, UnitEntry> m = new LinkedHashMap<>();
    // Joint angles
    m.put("j1_", new UnitEntry("DEG", "joint angle"));
    m.put("j2_", new UnitEntry("DEG", "joint angle"));
    m.put("j3_", new UnitEntry("DEG", "joint angle"));
    m.put("j4_", new UnitEntry("DEG", "joint angle"));
    m.put("j5_", new UnitEntry("DEG", "joint angle"));
    m.put("j6_", new UnitEntry("DEG", "joint angle"));
    m.put("j7_", new UnitEntry("DEG", "joint angle"));
    // Generic engineering prefixes
    m.put("acc_",  new UnitEntry("M-PER-SEC2",  "linear acceleration"));
    m.put("rpm_",  new UnitEntry("REV-PER-MIN", "revolutions per minute"));
    m.put("mdot_", new UnitEntry("KG-PER-SEC",  "mass flow rate"));
    m.put("vib_",  new UnitEntry("G",           "vibration RMS (g-force)"));
    // Rocket / LUMEN conventions
    m.put("tc_",        new UnitEntry("K",   "thermocouple (Kelvin)"));
    m.put("pc_",        new UnitEntry("BAR", "chamber pressure"));
    m.put("p_inj_",     new UnitEntry("BAR", "injector pressure"));
    m.put("p_tank_",    new UnitEntry("BAR", "tank pressure"));
    m.put("t_coolant_", new UnitEntry("K",   "coolant temperature (cryo)"));
    m.put("t_lox_",     new UnitEntry("K",   "LOX inlet temperature"));
    m.put("lch4_temperature",     new UnitEntry("K",     "LCH4 temperature"));
    m.put("turbopump_bearing_temp", new UnitEntry("DEG_C", "turbopump bearing temp"));
    m.put("turbopump_vibration",    new UnitEntry("G",     "turbopump vibration RMS"));
    m.put("strain_",    new UnitEntry("UNITLESS", "strain (dimensionless)"));
    PREFIX_TO_QUDT = java.util.Collections.unmodifiableMap(m);
  }

  // Resistance-welding cap codes (CM_*, W1_*, W2_*, WC_*) disambiguate by tail
  // ONLY for current ("_I") and voltage ("_U"). The remaining cap tails
  // ("_p" — power? pressure?  "_t" — time? temperature?) are deliberately
  // left AMBIGUOUS for AI1v Phase 2.
  private static final Map<String, UnitEntry> WELDING_CAP_TAIL = Map.of(
    "_I", new UnitEntry("A", "current (ampere)"),
    "_U", new UnitEntry("V", "voltage (volt)")
  );

  /**
   * Infer the QUDT unit IRI for a Timeseries channel {@code field} name.
   *
   * <p>Resolution order: longest matching {@link #SUFFIX_TO_QUDT} entry first,
   * then {@link #PREFIX_TO_QUDT}, then the welding-cap special-case. Returns
   * {@link Optional#empty()} only for null / blank inputs; an ambiguous field
   * is signalled by a {@link UnitGuess} with {@link Tier#AMBIGUOUS} and a
   * {@code null} {@link UnitGuess#unitIri()}.
   *
   * <p>Callers may treat empty-optional and AMBIGUOUS identically (skip the
   * annotation) or branch on AMBIGUOUS to log a structured warning that
   * Phase 2 should pick up.
   *
   * @param fieldName the channel's {@code field} column from
   *                  {@code timeseries.channel_metadata}; may be null
   * @return the inferred unit, or {@link Optional#empty()} if {@code fieldName}
   *         is null / blank
   */
  public Optional<UnitGuess> infer(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return Optional.empty();
    }

    // 1. Suffix match (longest-first via insertion order in SUFFIX_TO_QUDT)
    for (Map.Entry<String, UnitEntry> e : SUFFIX_TO_QUDT.entrySet()) {
      if (fieldName.endsWith(e.getKey())) {
        return Optional.of(new UnitGuess(
          QUDT_UNIT_PREFIX + e.getValue().qudtLocal(),
          e.getValue().label(),
          Tier.SUFFIX
        ));
      }
    }

    // 2. Prefix match
    for (Map.Entry<String, UnitEntry> e : PREFIX_TO_QUDT.entrySet()) {
      if (fieldName.startsWith(e.getKey())) {
        return Optional.of(new UnitGuess(
          QUDT_UNIT_PREFIX + e.getValue().qudtLocal(),
          e.getValue().label(),
          Tier.PREFIX_HEURISTIC
        ));
      }
    }

    // 3. Welding-cap (CM_*, W1_*, W2_*, WC_*) — disambiguate _I / _U only
    if (fieldName.length() >= 4 && fieldName.charAt(2) == '_') {
      String head = fieldName.substring(0, 2);
      if (head.equals("CM") || head.equals("W1") || head.equals("W2") || head.equals("WC")) {
        String tail = fieldName.substring(2);
        UnitEntry hit = WELDING_CAP_TAIL.get(tail);
        if (hit != null) {
          return Optional.of(new UnitGuess(
            QUDT_UNIT_PREFIX + hit.qudtLocal(),
            hit.label(),
            Tier.WELDING_CAP
          ));
        }
        // Recognised cap prefix but unresolved tail → AMBIGUOUS (handed to Phase 2)
        return Optional.of(new UnitGuess(null, null, Tier.AMBIGUOUS));
      }
    }

    // 4. No deterministic match — the caller may treat this identically to
    //    AMBIGUOUS but we distinguish for log-level / metrics granularity.
    return Optional.of(new UnitGuess(null, null, Tier.AMBIGUOUS));
  }

  // ── nested types ────────────────────────────────────────────────────────

  /**
   * Resolution tier of an {@link #infer(String)} hit. The order roughly
   * corresponds to confidence: {@link #SUFFIX} is the strongest signal
   * (explicit unit token at the end of the name), {@link #WELDING_CAP}
   * the most domain-specific.
   */
  public enum Tier {
    /** Matched on a deterministic suffix token like {@code _mm}, {@code _N}, {@code _bar}. */
    SUFFIX,
    /** Matched on a domain prefix like {@code tc_}, {@code pc_}, {@code rpm_}. */
    PREFIX_HEURISTIC,
    /** Matched a resistance-welding cap pattern ({@code CM_I}, {@code W1_U}, …). */
    WELDING_CAP,
    /** No deterministic match — Phase 2 (LLM with parent-DO context) should resolve. */
    AMBIGUOUS
  }

  /**
   * One inference result.  When {@link #tier()} is {@link Tier#AMBIGUOUS}
   * the {@link #unitIri()} and {@link #label()} are {@code null}.
   *
   * @param unitIri QUDT canonical IRI, e.g. {@code http://qudt.org/vocab/unit/MilliM}
   * @param label   human-readable label (free text — not a QUDT rdfs:label per se)
   * @param tier    which resolution path produced this match
   */
  public record UnitGuess(String unitIri, String label, Tier tier) {
    /** Convenience: true when this guess carries a concrete unit IRI. */
    public boolean isResolved() {
      return unitIri != null;
    }
  }

  /** Internal helper for the static lookup tables. */
  private record UnitEntry(String qudtLocal, String label) {}
}
