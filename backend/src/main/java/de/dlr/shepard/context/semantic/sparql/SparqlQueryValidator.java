package de.dlr.shepard.context.semantic.sparql;

/**
 * N1f — lightweight guard that enforces the read-only SPARQL policy.
 *
 * <p>The SPARQL proxy endpoint at
 * {@code GET/POST /v2/semantic/{repoAppId}/sparql} only permits
 * {@code SELECT} and {@code ASK} queries. Mutation forms
 * ({@code CONSTRUCT}, {@code DESCRIBE}, {@code INSERT}, {@code DELETE},
 * {@code UPDATE}, {@code DROP}, {@code CREATE}, {@code LOAD},
 * {@code CLEAR}, {@code ADD}, {@code MOVE}, {@code COPY}) are rejected
 * with 400 so that read-only callers can't inadvertently (or maliciously)
 * alter the RDF graph through the proxy.
 *
 * <p>Detection is performed by trimming leading whitespace and SPARQL
 * prefix declarations (lines starting with {@code PREFIX} or
 * {@code BASE}), then uppercasing and matching the first token against
 * the allowed and forbidden sets. This is intentionally simple — a full
 * SPARQL parser would be a heavy dependency for a purely defensive gate.
 * A motivated attacker who controls the query parameter can always
 * craft a query that slips past string-matching. The real security
 * boundary is the Neo4j / Fuseki process's own access controls; this
 * validator is an operator-convenience layer that prevents accidental
 * mutations and meets the {@code N1f} read-only requirement.
 */
public final class SparqlQueryValidator {

  // Mutation keywords that must be rejected.
  private static final java.util.Set<String> MUTATION_KEYWORDS = java.util.Set.of(
    "CONSTRUCT",
    "DESCRIBE",
    "INSERT",
    "DELETE",
    "UPDATE",
    "DROP",
    "CREATE",
    "LOAD",
    "CLEAR",
    "ADD",
    "MOVE",
    "COPY",
    "INSERT DATA",
    "DELETE DATA",
    "DELETE WHERE"
  );

  // Allowed query-form keywords.
  private static final java.util.Set<String> ALLOWED_KEYWORDS = java.util.Set.of("SELECT", "ASK");

  private SparqlQueryValidator() {}

  /**
   * Validate that {@code query} is a read-only SPARQL operation.
   *
   * @param query the raw SPARQL string (may include PREFIX declarations)
   * @return a {@link ValidationResult} indicating allowed or rejected
   */
  public static ValidationResult validate(String query) {
    if (query == null || query.isBlank()) {
      return ValidationResult.rejected("sparql.empty-query", "SPARQL query must not be empty.");
    }

    String keyword = extractFirstKeyword(query);
    if (keyword == null) {
      return ValidationResult.rejected(
        "sparql.unrecognised-form",
        "Could not determine the SPARQL query form. Only SELECT and ASK queries are supported."
      );
    }

    // Check mutation forms first — reject before allowing.
    for (String mutation : MUTATION_KEYWORDS) {
      if (keyword.equals(mutation)) {
        return ValidationResult.rejected(
          "sparql.read-only",
          "Only SELECT and ASK queries are permitted. Received: " + keyword + "."
        );
      }
    }

    if (ALLOWED_KEYWORDS.contains(keyword)) {
      return ValidationResult.allowed();
    }

    return ValidationResult.rejected(
      "sparql.read-only",
      "Only SELECT and ASK queries are permitted. Received: " + keyword + "."
    );
  }

  /**
   * Strip leading comments, PREFIX/BASE declarations and whitespace, then
   * return the first uppercase keyword token.
   */
  static String extractFirstKeyword(String query) {
    // Split on newlines, skip blank lines, comment lines (#...) and
    // PREFIX / BASE declarations.
    String[] lines = query.split("\\r?\\n");
    for (String raw : lines) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      String upper = line.toUpperCase(java.util.Locale.ROOT);
      if (upper.startsWith("PREFIX ") || upper.startsWith("BASE ")) continue;
      // Return the first space-delimited token.
      int space = line.indexOf(' ');
      String token = (space >= 0 ? line.substring(0, space) : line).toUpperCase(java.util.Locale.ROOT);
      return token;
    }
    return null;
  }

  /**
   * Result of SPARQL query validation. Carries the error type and
   * human-readable detail only when rejected; both are {@code null}
   * for an allowed query.
   */
  public static final class ValidationResult {

    private final boolean allowed;
    private final String errorType;
    private final String errorDetail;

    private ValidationResult(boolean allowed, String errorType, String errorDetail) {
      this.allowed = allowed;
      this.errorType = errorType;
      this.errorDetail = errorDetail;
    }

    static ValidationResult allowed() {
      return new ValidationResult(true, null, null);
    }

    static ValidationResult rejected(String errorType, String errorDetail) {
      return new ValidationResult(false, errorType, errorDetail);
    }

    /** @return {@code true} if the query is permitted. */
    public boolean isAllowed() {
      return allowed;
    }

    /** RFC 7807 problem type string, or {@code null} for allowed queries. */
    public String getErrorType() {
      return errorType;
    }

    /** Human-readable rejection reason, or {@code null} for allowed queries. */
    public String getErrorDetail() {
      return errorDetail;
    }
  }
}
