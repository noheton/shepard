package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * PM1c — covers {@link VersionRange} parsing + acceptance.
 */
class VersionRangeTest {

  @Test
  void nullInput_acceptsEverything() {
    VersionRange r = VersionRange.parse(null);
    assertThat(r.isAnyAllowed()).isTrue();
    assertThat(r.accepts("0.0.1")).isTrue();
    assertThat(r.accepts("99.99.99")).isTrue();
  }

  @Test
  void emptyInput_acceptsEverything() {
    VersionRange r = VersionRange.parse("");
    assertThat(r.isAnyAllowed()).isTrue();
    assertThat(r.accepts("1.0.0")).isTrue();
  }

  @Test
  void exactVersion_matchesOnlyItself() {
    VersionRange r = VersionRange.parse("1.2.3");
    assertThat(r.accepts("1.2.3")).isTrue();
    assertThat(r.accepts("1.2.4")).isFalse();
    assertThat(r.accepts("1.2.2")).isFalse();
  }

  @Test
  void inclusiveLowerBound_acceptsAtBoundary() {
    VersionRange r = VersionRange.parse("[1.0.0,)");
    assertThat(r.accepts("1.0.0")).isTrue();
    assertThat(r.accepts("1.0.1")).isTrue();
    assertThat(r.accepts("0.9.9")).isFalse();
  }

  @Test
  void exclusiveLowerBound_rejectsAtBoundary() {
    VersionRange r = VersionRange.parse("(1.0.0,)");
    assertThat(r.accepts("1.0.0")).isFalse();
    assertThat(r.accepts("1.0.1")).isTrue();
  }

  @Test
  void inclusiveUpperBound_acceptsAtBoundary() {
    VersionRange r = VersionRange.parse("(,2.0.0]");
    assertThat(r.accepts("2.0.0")).isTrue();
    assertThat(r.accepts("2.0.1")).isFalse();
    assertThat(r.accepts("1.9.9")).isTrue();
  }

  @Test
  void exclusiveUpperBound_rejectsAtBoundary() {
    VersionRange r = VersionRange.parse("[1.0.0,2.0.0)");
    assertThat(r.accepts("2.0.0")).isFalse();
    assertThat(r.accepts("1.9.9")).isTrue();
    assertThat(r.accepts("1.0.0")).isTrue();
  }

  @Test
  void bothBounds_acceptsInsideRange() {
    VersionRange r = VersionRange.parse("[1.5,3.0)");
    assertThat(r.accepts("1.0")).isFalse();
    assertThat(r.accepts("1.5")).isTrue();
    assertThat(r.accepts("2.0")).isTrue();
    assertThat(r.accepts("3.0")).isFalse();
    assertThat(r.accepts("3.1")).isFalse();
  }

  @Test
  void semverNumericCompare_doesNotLexicographicallyOrder() {
    VersionRange r = VersionRange.parse("[1.0,2.0)");
    // Lexicographic would put "1.10" < "1.2"; numeric must put it >.
    assertThat(r.accepts("1.10")).isTrue();
    assertThat(r.accepts("1.2")).isTrue();
    assertThat(VersionRange.compare("1.10.0", "1.2.0")).isGreaterThan(0);
  }

  @Test
  void preReleaseSuffix_orderedBeforePlainVersion() {
    // SemVer 2.0.0 §11: 1.0.0-rc1 < 1.0.0
    assertThat(VersionRange.compare("1.0.0-rc1", "1.0.0")).isLessThan(0);
    assertThat(VersionRange.compare("1.0.0", "1.0.0-SNAPSHOT")).isGreaterThan(0);
  }

  @Test
  void malformedRange_throwsIllegalArgument() {
    assertThatThrownBy(() -> VersionRange.parse("[1.0"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Malformed");
  }

  @Test
  void blankVersion_neverSatisfiesBoundedRange() {
    VersionRange r = VersionRange.parse("[1.0,)");
    assertThat(r.accepts(null)).isFalse();
    assertThat(r.accepts("")).isFalse();
    assertThat(r.accepts("   ")).isFalse();
  }

  // ─── PM1b2: operator-comma syntax (>=5.2.0,<6) ───────────────────

  @Test
  void operatorComma_geAndLt_acceptsInRange() {
    // The shape every PluginManifest's shepardCompatibility() uses.
    VersionRange r = VersionRange.parse(">=5.2.0,<6");
    assertThat(r.accepts("5.2.0")).isTrue();
    assertThat(r.accepts("5.2.1")).isTrue();
    assertThat(r.accepts("5.9.99")).isTrue();
    assertThat(r.accepts("5.1.99")).isFalse();
    assertThat(r.accepts("6.0.0")).isFalse();
    assertThat(r.accepts("4.0")).isFalse();
  }

  @Test
  void operatorComma_singleClause_geOnly() {
    VersionRange r = VersionRange.parse(">=2.0");
    assertThat(r.accepts("2.0")).isTrue();
    assertThat(r.accepts("2.0.1")).isTrue();
    assertThat(r.accepts("1.99")).isFalse();
  }

  @Test
  void operatorComma_singleClause_ltOnly() {
    VersionRange r = VersionRange.parse("<3.0");
    assertThat(r.accepts("2.9.9")).isTrue();
    assertThat(r.accepts("3.0")).isFalse();
    assertThat(r.accepts("3.0.1")).isFalse();
  }

  @Test
  void operatorComma_equalsOperator_matchesExact() {
    VersionRange r = VersionRange.parse("=1.2.3");
    assertThat(r.accepts("1.2.3")).isTrue();
    assertThat(r.accepts("1.2.4")).isFalse();
  }

  @Test
  void operatorComma_emptyClause_throws() {
    assertThatThrownBy(() -> VersionRange.parse(">=1.0,"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void operatorComma_malformedClause_throws() {
    assertThatThrownBy(() -> VersionRange.parse(">=not-a-version"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void operatorComma_semverNumericCompare() {
    VersionRange r = VersionRange.parse(">=1.2,<1.10");
    assertThat(r.accepts("1.2")).isTrue();
    assertThat(r.accepts("1.9")).isTrue();
    // 1.10 must be rejected (semver-numeric: 1.10 > 1.9 > 1.2).
    assertThat(r.accepts("1.10")).isFalse();
  }

  @Test
  void operatorComma_bareClauseTreatedAsExact() {
    // Composer-style: a bare version literal between commas is "= that version".
    VersionRange r = VersionRange.parse(">=1.0,1.5.0");
    assertThat(r.accepts("1.5.0")).isTrue();
    assertThat(r.accepts("1.5.1")).isFalse(); // = clause excludes other 1.5+
    assertThat(r.accepts("1.0")).isFalse();
  }

  @Test
  void snapshotInclusiveLowerBound_acceptsSnapshotAndRelease() {
    // V6 plugin compat shape: >=6.0.0-SNAPSHOT,<7 accepts both the
    // dev SNAPSHOT build and any 6.x release, but not 7.0.0+.
    VersionRange r = VersionRange.parse(">=6.0.0-SNAPSHOT,<7");
    assertThat(r.accepts("6.0.0-SNAPSHOT")).isTrue(); // dev build
    assertThat(r.accepts("6.0.0")).isTrue();           // release
    assertThat(r.accepts("6.1.0")).isTrue();
    assertThat(r.accepts("6.9.99")).isTrue();
    assertThat(r.accepts("5.9.0")).isFalse();
    assertThat(r.accepts("7.0.0")).isFalse();
    // 7.0.0-SNAPSHOT < 7.0.0 in semver §11, so it does pass <7.
    // This is intentional: next-major dev builds won't break plugin loading.
    assertThat(r.accepts("7.0.0-SNAPSHOT")).isTrue();
  }
}
