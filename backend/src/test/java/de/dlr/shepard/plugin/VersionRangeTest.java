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
}
