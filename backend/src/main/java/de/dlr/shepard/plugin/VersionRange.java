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
 *   <li><strong>Operator-comma range</strong> (PM1b2): {@code ">=5.2.0,<6"} —
 *       npm/Composer-style shorthand favoured by every
 *       {@code shepardCompatibility()} declaration today. Each
 *       comma-separated clause is an operator
 *       ({@code >}, {@code >=}, {@code <}, {@code <=}, {@code =})
 *       followed by a version literal; all clauses must hold
 *       simultaneously (logical AND). Two operator forms suffice to
 *       cover the "needs at least X, breaks at Y" idiom every
 *       PluginManifest writes; we deliberately don't try to be
 *       Composer-complete (no caret / tilde / pipe-OR).</li>
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

  /**
   * PM1b2 — matches a single operator-comma clause:
   * {@code ">=5.2.0"}, {@code "<6"}, {@code ">=1.0.0-rc1"}.
   * The operator is captured as group 1; the version literal as
   * group 2. The version literal is the same dotted-number+suffix
   * shape {@link #VERSION} accepts.
   */
  private static final Pattern OPERATOR_CLAUSE = Pattern.compile(
    "^\\s*(>=|<=|>|<|=)\\s*([0-9]+(?:\\.[0-9]+)*(?:[-+][A-Za-z0-9.+-]*)?)\\s*$"
  );

  private final String raw;
  private final boolean anyAllowed;
  private final boolean exactMatch;
  private final String exactValue;
  private final String low;
  private final String high;
  private final boolean lowInclusive;
  private final boolean highInclusive;
  /**
   * PM1b2 — operator-comma clauses (e.g. {@code ">=5.2.0"} + {@code "<6"}).
   * Null for non-operator-comma ranges; non-null + non-empty otherwise.
   * Each {@link OperatorClause} carries its operator and version
   * literal; {@link #accepts(String)} applies the logical AND across
   * all clauses.
   */
  private final OperatorClause[] operatorClauses;

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
    this(raw, anyAllowed, exactMatch, exactValue, low, high, lowInclusive, highInclusive, null);
  }

  private VersionRange(
    String raw,
    boolean anyAllowed,
    boolean exactMatch,
    String exactValue,
    String low,
    String high,
    boolean lowInclusive,
    boolean highInclusive,
    OperatorClause[] operatorClauses
  ) {
    this.raw = raw;
    this.anyAllowed = anyAllowed;
    this.exactMatch = exactMatch;
    this.exactValue = exactValue;
    this.low = low;
    this.high = high;
    this.lowInclusive = lowInclusive;
    this.highInclusive = highInclusive;
    this.operatorClauses = operatorClauses;
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
    // PM1b2 — operator-comma shape (`>=5.2.0,<6`). Detect by either a
    // leading operator character or the presence of a comma at the
    // top level (no bracket prefix). Operator characters that would
    // otherwise be unambiguous with the exact-version literal form
    // disqualify the exact-match short-circuit below.
    if (
      trimmed.startsWith(">") ||
      trimmed.startsWith("<") ||
      trimmed.startsWith("=") ||
      trimmed.contains(",")
    ) {
      // -1 limit so trailing empty clauses (e.g. ">=1.0,") are
      // preserved and flagged as malformed below.
      String[] parts = trimmed.split(",", -1);
      OperatorClause[] clauses = new OperatorClause[parts.length];
      for (int i = 0; i < parts.length; i++) {
        String part = parts[i].trim();
        if (part.isEmpty()) {
          throw new IllegalArgumentException("Empty clause in operator-comma range: '" + input + "'");
        }
        Matcher m = OPERATOR_CLAUSE.matcher(part);
        if (!m.matches()) {
          // A bare-literal clause (no operator) is treated as `=` —
          // matches Composer's convention. If even that doesn't parse
          // as a version, throw.
          if (!VERSION.matcher(part).matches()) {
            throw new IllegalArgumentException(
              "Malformed clause '" + part + "' in operator-comma range: '" + input + "'"
            );
          }
          clauses[i] = new OperatorClause("=", part);
        } else {
          clauses[i] = new OperatorClause(m.group(1), m.group(2));
        }
      }
      return new VersionRange(trimmed, false, false, null, null, null, false, false, clauses);
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
    if (operatorClauses != null) {
      // PM1b2 — logical AND across all operator clauses.
      for (OperatorClause c : operatorClauses) {
        if (!c.accepts(trimmed)) {
          return false;
        }
      }
      return true;
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

  /**
   * PM1b2 — a single operator-comma clause (e.g. {@code ">=5.2.0"}).
   * The operator is one of {@code >}, {@code >=}, {@code <},
   * {@code <=}, {@code =}; the version literal is verbatim.
   */
  private record OperatorClause(String operator, String version) {
    boolean accepts(String candidate) {
      int cmp = compare(candidate, version);
      return switch (operator) {
        case ">=" -> cmp >= 0;
        case ">" -> cmp > 0;
        case "<=" -> cmp <= 0;
        case "<" -> cmp < 0;
        case "=" -> cmp == 0;
        default -> false;
      };
    }
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
