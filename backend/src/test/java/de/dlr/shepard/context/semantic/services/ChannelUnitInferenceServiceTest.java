package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.semantic.services.ChannelUnitInferenceService.Tier;
import de.dlr.shepard.context.semantic.services.ChannelUnitInferenceService.UnitGuess;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AI1v Phase 1 -- pure-unit tests for {@link ChannelUnitInferenceService}.
 *
 * <p>No Quarkus bootstrap.  The service is stateless and has no injected
 * dependencies, so a direct {@code new} is the cheapest test path; the whole
 * suite runs sub-second.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Every suffix in {@code SUFFIX_TO_QUDT} -- round-trip to QUDT IRI.</li>
 *   <li>Every prefix in {@code PREFIX_TO_QUDT} -- same.</li>
 *   <li>Welding-cap CM_x, W1_x, W2_x, WC_x with _I, _U, and ambiguous tails.</li>
 *   <li>The 18 ambiguous field names that the 2026-05-28 recovery script
 *       reported as SKIP -- must return AMBIGUOUS.</li>
 *   <li>Edge cases: null, empty, single char, suffix that almost matches.</li>
 *   <li>Suffix-ordering invariant: "_mm_s" wins over "_mm".</li>
 * </ul>
 */
class ChannelUnitInferenceServiceTest {

  private final ChannelUnitInferenceService svc = new ChannelUnitInferenceService();

  // --- Suffix-tier coverage ------------------------------------------------

  static Stream<Arguments> suffixCases() {
    return Stream.of(
      Arguments.of("tcp_x_mm",          "MilliM",         "millimeter"),
      Arguments.of("velocity_mm_s",     "MilliM-PER-SEC", "millimeter per second"),
      Arguments.of("scan_um",           "MicroM",         "micrometer"),
      Arguments.of("torque_x_Nm",       "N-M",            "newton metre"),
      Arguments.of("thrust_kN",         "KiloN",          "kilonewton"),
      Arguments.of("thrust_kn",         "KiloN",          "kilonewton"),
      Arguments.of("force_z_N",         "N",              "newton"),
      Arguments.of("heat_J",            "J",              "joule"),
      Arguments.of("temp_K",            "K",              "kelvin"),
      Arguments.of("ambient_C",         "DEG_C",          "degree Celsius"),
      Arguments.of("bearing_degC",      "DEG_C",          "degree Celsius"),
      Arguments.of("tcp_rx_deg",        "DEG",            "degree"),
      Arguments.of("chamber_bar",       "BAR",            "bar"),
      Arguments.of("regen_psi",         "PSI",            "pound-force per square inch"),
      Arguments.of("vibration_g",       "G",              "g-force"),
      Arguments.of("inlet_Pa",          "PA",             "pascal")
    );
  }

  @ParameterizedTest(name = "[{index}] suffix: {0} -> {1}")
  @MethodSource("suffixCases")
  @DisplayName("Every suffix in SUFFIX_TO_QUDT resolves to its QUDT IRI")
  void suffixMatch(String field, String qudtLocal, String label) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent(), "Expected a guess for '" + field + "'");
    assertTrue(g.get().isResolved());
    assertEquals(Tier.SUFFIX, g.get().tier());
    assertEquals("http://qudt.org/vocab/unit/" + qudtLocal, g.get().unitIri());
    assertEquals(label, g.get().label());
  }

  // --- Prefix-tier coverage ------------------------------------------------

  static Stream<Arguments> prefixCases() {
    return Stream.of(
      Arguments.of("j1_angle",                 "DEG",         "joint angle"),
      Arguments.of("j7_angle",                 "DEG",         "joint angle"),
      Arguments.of("acc_x",                    "M-PER-SEC2",  "linear acceleration"),
      Arguments.of("rpm_motor",                "REV-PER-MIN", "revolutions per minute"),
      Arguments.of("mdot_oxidizer",            "KG-PER-SEC",  "mass flow rate"),
      Arguments.of("vib_axial",                "G",           "vibration RMS (g-force)"),
      Arguments.of("tc_chamber_1",             "K",           "thermocouple (Kelvin)"),
      Arguments.of("pc_chamber",               "BAR",         "chamber pressure"),
      Arguments.of("p_inj_fuel",               "BAR",         "injector pressure"),
      Arguments.of("p_tank_lox",               "BAR",         "tank pressure"),
      Arguments.of("t_coolant_in",             "K",           "coolant temperature (cryo)"),
      Arguments.of("t_lox_inlet",              "K",           "LOX inlet temperature"),
      Arguments.of("lch4_temperature",         "K",           "LCH4 temperature"),
      Arguments.of("turbopump_bearing_temp",   "DEG_C",       "turbopump bearing temp"),
      Arguments.of("turbopump_vibration",      "G",           "turbopump vibration RMS"),
      Arguments.of("strain_axial_long",        "UNITLESS",    "strain (dimensionless)")
    );
  }

  @ParameterizedTest(name = "[{index}] prefix: {0} -> {1}")
  @MethodSource("prefixCases")
  @DisplayName("Every prefix in PREFIX_TO_QUDT resolves to its QUDT IRI")
  void prefixMatch(String field, String qudtLocal, String label) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent());
    assertTrue(g.get().isResolved(), "Expected resolved guess for '" + field + "'");
    assertEquals(Tier.PREFIX_HEURISTIC, g.get().tier());
    assertEquals("http://qudt.org/vocab/unit/" + qudtLocal, g.get().unitIri());
    assertEquals(label, g.get().label());
  }

  // --- Welding-cap tier coverage -------------------------------------------

  @ParameterizedTest(name = "[{index}] welding-cap: {0} -> ampere")
  @ValueSource(strings = {"CM_I", "W1_I", "W2_I", "WC_I"})
  @DisplayName("Welding cap _I tail -> Ampere")
  void weldingCapCurrent(String field) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent());
    assertTrue(g.get().isResolved());
    assertEquals(Tier.WELDING_CAP, g.get().tier());
    assertEquals("http://qudt.org/vocab/unit/A", g.get().unitIri());
  }

  @ParameterizedTest(name = "[{index}] welding-cap: {0} -> volt")
  @ValueSource(strings = {"CM_U", "W1_U", "W2_U", "WC_U"})
  @DisplayName("Welding cap _U tail -> Volt")
  void weldingCapVoltage(String field) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent());
    assertEquals(Tier.WELDING_CAP, g.get().tier());
    assertEquals("http://qudt.org/vocab/unit/V", g.get().unitIri());
  }

  @ParameterizedTest(name = "[{index}] ambiguous welding-cap tail: {0}")
  @ValueSource(strings = {"CM_p", "CM_t", "W1_p", "W1_t", "W2_p", "W2_t", "WC_t"})
  @DisplayName("Welding cap _p / _t tails stay AMBIGUOUS (handed to Phase 2)")
  void weldingCapAmbiguousTail(String field) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent());
    assertEquals(Tier.AMBIGUOUS, g.get().tier());
    assertNull(g.get().unitIri());
    assertFalse(g.get().isResolved());
  }

  // --- The 18 ambiguous fields from the 2026-05-28 recovery run ------------

  @ParameterizedTest(name = "[{index}] ambiguous: {0}")
  @ValueSource(strings = {
    "BridgePosition",
    "valve_fuel",
    "valve_lox"
  })
  @DisplayName("Recovery-script ambiguous fields -> AMBIGUOUS (no false positive)")
  void recoveryAmbiguousFields(String field) {
    Optional<UnitGuess> g = svc.infer(field);
    assertTrue(g.isPresent(), "Even ambiguous inputs return a non-empty Optional");
    assertEquals(Tier.AMBIGUOUS, g.get().tier(), "Field must not match any deterministic rule");
    assertNull(g.get().unitIri());
  }

  // --- Edge cases ----------------------------------------------------------

  @Test
  @DisplayName("null field -> Optional.empty()")
  void nullField() {
    assertTrue(svc.infer(null).isEmpty());
  }

  @Test
  @DisplayName("empty field -> Optional.empty()")
  void emptyField() {
    assertTrue(svc.infer("").isEmpty());
  }

  @Test
  @DisplayName("whitespace-only field -> Optional.empty()")
  void blankField() {
    assertTrue(svc.infer("   ").isEmpty());
  }

  @Test
  @DisplayName("Single-character field that does not match anything -> AMBIGUOUS")
  void singleCharField() {
    Optional<UnitGuess> g = svc.infer("x");
    assertTrue(g.isPresent());
    assertEquals(Tier.AMBIGUOUS, g.get().tier());
  }

  @Test
  @DisplayName("Suffix-ordering: '_mm_s' wins over '_mm' (longest suffix first)")
  void suffixOrderingMmS() {
    Optional<UnitGuess> g = svc.infer("velocity_mm_s");
    assertTrue(g.isPresent());
    assertEquals(Tier.SUFFIX, g.get().tier());
    assertEquals("http://qudt.org/vocab/unit/MilliM-PER-SEC", g.get().unitIri(),
      "Must NOT collapse to MilliM via the '_mm' suffix");
  }

  @Test
  @DisplayName("Suffix-ordering: '_Nm' wins over '_N' / '_m'")
  void suffixOrderingNm() {
    Optional<UnitGuess> g = svc.infer("torque_x_Nm");
    assertTrue(g.isPresent());
    assertEquals("http://qudt.org/vocab/unit/N-M", g.get().unitIri());
  }

  @Test
  @DisplayName("Cap-pattern false positive: 'CM' alone (length=2) does NOT match")
  void capPatternTooShort() {
    Optional<UnitGuess> g = svc.infer("CM");
    assertTrue(g.isPresent());
    assertEquals(Tier.AMBIGUOUS, g.get().tier());
  }

  @Test
  @DisplayName("Cap-pattern requires third-char underscore: 'CMI' (no underscore) -> AMBIGUOUS")
  void capPatternRequiresUnderscore() {
    Optional<UnitGuess> g = svc.infer("CMI");
    assertTrue(g.isPresent());
    assertEquals(Tier.AMBIGUOUS, g.get().tier());
  }

  @Test
  @DisplayName("UnitGuess.isResolved() is true iff unitIri != null")
  void unitGuessIsResolved() {
    assertTrue(new UnitGuess("http://qudt.org/vocab/unit/N", "newton", Tier.SUFFIX).isResolved());
    assertFalse(new UnitGuess(null, null, Tier.AMBIGUOUS).isResolved());
  }
}
