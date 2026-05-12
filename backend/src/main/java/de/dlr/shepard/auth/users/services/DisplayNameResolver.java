package de.dlr.shepard.auth.users.services;

import de.dlr.shepard.auth.users.entities.User;

/**
 * Derives the rendered display name for a {@link User} per
 * {@code aidocs/16 U1b}. Three-tier fallback:
 *
 * <ol>
 *   <li>{@code displayName} override if the user set one.</li>
 *   <li>{@code firstName lastName}, trimmed, when at least one half is
 *       non-blank.</li>
 *   <li>Redacted-username fallback for the cryptic-Keycloak-username
 *       case ({@code aidocs/22} / issue #694 / mitigates #628):
 *       take the trailing segment after the last {@code :} or
 *       {@code /}, then keep the first 8 characters with an ellipsis
 *       when the result still looks UUID-shaped.</li>
 * </ol>
 *
 * <p>Static helper — every code path that today writes "Created by
 * &lt;username&gt;" or similar should route through here so the
 * fallback policy stays in one place. The audit-trail render switch
 * is queued separately (per the U1b row notes).
 */
public final class DisplayNameResolver {

  private DisplayNameResolver() {}

  /**
   * Derives the effective display name. Never returns null, never
   * returns blank — falls back to {@code "(anonymous)"} for the
   * pathological null-everywhere case so callers can avoid an
   * NPE without re-implementing the fallback.
   */
  public static String effectiveDisplayName(User user) {
    if (user == null) return "(anonymous)";
    String override = user.getDisplayName();
    if (override != null && !override.isBlank()) return override.trim();

    String first = user.getFirstName();
    String last = user.getLastName();
    String composed = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    if (!composed.isEmpty()) return composed;

    return redactUsername(user.getUsername());
  }

  /**
   * Trailing-segment + truncate-to-8 redaction. Public for the
   * audit-trail render switch (U1b follow-up) and for tests.
   */
  public static String redactUsername(String rawUsername) {
    if (rawUsername == null || rawUsername.isBlank()) return "(anonymous)";
    String tail = rawUsername;
    int sep = Math.max(rawUsername.lastIndexOf('/'), rawUsername.lastIndexOf(':'));
    if (sep >= 0 && sep < rawUsername.length() - 1) {
      tail = rawUsername.substring(sep + 1);
    }
    // Keycloak subjects often look like 36-char UUIDs. Keep the
    // first 8 + ellipsis so the audit trail stays a stable handle
    // while not surfacing the full opaque id.
    if (tail.length() > 12 && looksUuidShaped(tail)) {
      return tail.substring(0, 8) + "…";
    }
    return tail;
  }

  private static boolean looksUuidShaped(String s) {
    // Rough heuristic: hex + hyphens, no spaces, at least 32 hex chars.
    int hexCount = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
        hexCount++;
      } else if (c != '-') {
        return false;
      }
    }
    return hexCount >= 32;
  }
}
