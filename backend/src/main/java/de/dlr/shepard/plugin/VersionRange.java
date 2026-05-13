package de.dlr.shepard.plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PM1c — tiny Maven-style version-range parser used by
 * {@link PluginRegistry} to enforce {@link PluginDependency#versionConstraint()}.
 *
 * <p>Deliberately in-tree (no {@code org.apache.maven:maven-artifact}
 * dependency added) — same posture as N1c2's ontology-bundle slug
 * parser. The grammar we support is the strict subset every plugin
 * actually writes:
 *
 * <ul>
 *   <li><strong>Exact version</strong>: {@code "1.2.3"} → matches only
 *       {@code "1.2.3"}. Mirrors Maven's "soft requirement" idiom.</li>
 *   <li><strong>Bounded range</strong>: {@code "[1.0,2.0)"} —
 *       {@code [} / {@code ]} = inclusive, {@code (} / {@code )} =
 *       exclusive. Either bound may be empty: {@code "[1.5,)"} =
 *       "at least 1.5"; {@code "(,3.0]"} = "up to 3.0 inclusive".</li>
 *   <li><strong>Wildcard</strong>: empty string or {@code null} →
 *       matches any version. The registry surfaces a WARN on parse;
 *       admitted because some dependencies genuinely don't care
 *       (e.g. "I need the existence of plugin X, any version").</li>
 * </ul>
 *
 * <p>Comparison is **semver-aware** for the common dotted-number
 * case: {@code 1.10.0 > 1.2.0} as expected, not lexicographic. A
 * pre-release suffix ({@code "-SNAPSHOT"}, {@code "-rc.1"}) is
 * preserved verbatim and compared lexicographically <em>after</em>
 * the numeric part — close enough for the plugin-dep use case
 * without dragging in a full semver library.
 */
public final class VersionRange {

  /** Matches the bounded-range syntax {@code [|(low,high]|)}. */
  private static final Pattern RANGE = Pattern.compile(
    "^\\s*([\\[(])\\s*([^,\\[\\](){}]*)\\s*,\\s*([^,\\[\\](){}]*)\\s*([\\])])\\s*$"
  );

  /** Matches a single dotted-number+optional-suffix version. */
  private static final Pattern VERSION = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)*)([-+].*)?\\s*$");

  private final String raw;
  private final boolean anyAllowed;
  private final boolean exactMatch;
  private final String exactValue;
  private final String low;
  private final String high;
  private final boolean lowInclusive;
  private final boolean highInclusive;

  private VersionRange(
    String raw,
    boolean anyAllowed,
    boolean exactMatch,
    String exactValue,
    String low,
    String high,
    boolean lowInclusive,
    boolean highInclusive
  ) {
    this.raw = raw;
    this.anyAllowed = anyAllowed;
    this.exactMatch = exactMatch;
    this.exactValue = exactValue;
    this.low = low;
    this.high = high;
    this.lowInclusive = lowInclusive;
    this.highInclusive = highInclusive;
  }

  /**
   * Parse a version-range string. Returns a wildcard range for
   * {@code null} / empty / whitespace input (admits everything).
   *
   * @throws IllegalArgumentException when the syntax is recognised
   *         as a range but malformed (missing comma, unmatched
   *         brackets, ...). Unparseable strings are the caller's
   *         declaration bug; the registry surfaces this as
   *         {@code plugin.dependency.version-mismatch} so the
   *         plugin lands FAILED with a clear message.
   */
  public static VersionRange parse(String input) {
    if (input == null || input.isBlank()) {
      return new VersionRange(input == null ? "" : input, true, false, null, null, null, false, false);
    }
    String trimmed = input.trim();
    // Bounded range?
    if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
      Matcher m = RANGE.matcher(trimmed);
      if (!m.matches()) {
        throw new IllegalArgumentException("Malformed version range: '" + input + "'");
      }
      boolean lowIncl = "[".equals(m.group(1));
      boolean highIncl = "]".equals(m.group(4));
      String lowVal = m.group(2).isEmpty() ? null : m.group(2);
      String highVal = m.group(3).isEmpty() ? null : m.group(3);
      return new VersionRange(trimmed, false, false, null, lowVal, highVal, lowIncl, highIncl);
    }
    // Otherwise treat as exact-version literal.
    if (!VERSION.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("Unparseable version literal: '" + input + "'");
    }
    return new VersionRange(trimmed, false, true, trimmed, null, null, false, false);
  }

  /**
   * Whether the given concrete version satisfies this range.
   *
   * <p>A {@code null} / blank version never satisfies a non-wildcard
   * range — same idiom as Maven's range arithmetic.
   */
  public boolean accepts(String version) {
    if (anyAllowed) {
      return true;
    }
    if (version == null || version.isBlank()) {
      return false;
    }
    String trimmed = version.trim();
    if (exactMatch) {
      return exactValue.equals(trimmed);
    }
    if (low != null) {
      int cmp = compare(trimmed, low);
      if (lowInclusive ? cmp < 0 : cmp <= 0) {
        return false;
      }
    }
    if (high != null) {
      int cmp = compare(trimmed, high);
      if (highInclusive ? cmp > 0 : cmp >= 0) {
        return false;
      }
    }
    return true;
  }

  /** The original input string (post-trim) — for diagnostic logging. */
  public String raw() {
    return raw;
  }

  /** Whether this range accepts any version. */
  public boolean isAnyAllowed() {
    return anyAllowed;
  }

  /**
   * Semver-aware comparison: split each version on {@code .},
   * compare numerically segment-by-segment; ties broken by the
   * suffix (lexicographic, with no-suffix &gt; any-suffix per
   * SemVer 2.0.0 §11).
   */
  static int compare(String a, String b) {
    String aBase = splitBase(a);
    String bBase = splitBase(b);
    String aSuffix = splitSuffix(a);
    String bSuffix = splitSuffix(b);

    String[] aParts = aBase.split("\\.");
    String[] bParts = bBase.split("\\.");
    int n = Math.max(aParts.length, bParts.length);
    for (int i = 0; i < n; i++) {
      int ai = i < aParts.length ? parseSegment(aParts[i]) : 0;
      int bi = i < bParts.length ? parseSegment(bParts[i]) : 0;
      if (ai != bi) {
        return Integer.compare(ai, bi);
      }
    }
    // Numeric segments equal — compare suffixes.
    // SemVer 2.0.0 §11: "When major, minor, and patch are equal, a
    // pre-release version has lower precedence than a normal version."
    if (aSuffix.isEmpty() && bSuffix.isEmpty()) {
      return 0;
    }
    if (aSuffix.isEmpty()) {
      return 1; // a has no suffix → newer
    }
    if (bSuffix.isEmpty()) {
      return -1;
    }
    return aSuffix.compareTo(bSuffix);
  }

  private static int parseSegment(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ex) {
      // Non-numeric segment — fall back to lexicographic for that
      // segment alone (rare; e.g. "1.0.0a"). Hash-collapse for a
      // stable order.
      return Math.abs(s.hashCode());
    }
  }

  private static String splitBase(String v) {
    int dash = v.indexOf('-');
    int plus = v.indexOf('+');
    int cut = (dash < 0) ? plus : (plus < 0 ? dash : Math.min(dash, plus));
    return cut < 0 ? v : v.substring(0, cut);
  }

  private static String splitSuffix(String v) {
    int dash = v.indexOf('-');
    int plus = v.indexOf('+');
    int cut = (dash < 0) ? plus : (plus < 0 ? dash : Math.min(dash, plus));
    return cut < 0 ? "" : v.substring(cut);
  }
}
