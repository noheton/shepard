package de.dlr.shepard.cli.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Resolved admin-CLI configuration: target shepard base URL and
 * API key. Sourced (in order of precedence) from command-line
 * flags, environment variables, or {@code ~/.shepard/admin.toml}.
 *
 * <p>The TOML reader is intentionally minimal — Phase 1 only needs
 * two scalar string keys at top level
 * ({@code url = "..."}, {@code apiKey = "..."}) — so we hand-roll a
 * tiny parser rather than dragging in a TOML dependency. Anything
 * not understood is ignored.
 *
 * <p>The class is immutable. Use {@link AdminConfigLoader} to build
 * one.
 */
public final class AdminConfig {

  /** Default config-file location: {@code ~/.shepard/admin.toml}. */
  public static final String DEFAULT_CONFIG_PATH = ".shepard/admin.toml";

  /** Default URL when nothing else is set — local docker-compose. */
  public static final String DEFAULT_URL = "http://localhost:8080";

  /** Env-var name for the target URL. */
  public static final String ENV_URL = "SHEPARD_ADMIN_URL";

  /** Env-var name for the API key. */
  public static final String ENV_API_KEY = "SHEPARD_ADMIN_API_KEY";

  private final String url;
  private final String apiKey;

  public AdminConfig(String url, String apiKey) {
    this.url = url;
    this.apiKey = apiKey;
  }

  public String getUrl() {
    return url;
  }

  public Optional<String> getApiKey() {
    return Optional.ofNullable(apiKey);
  }

  /**
   * Tiny TOML-ish reader. Only handles {@code key = "value"} pairs
   * at the top level; ignores comments (lines starting with
   * {@code #}) and blank lines. Returns an empty map if the file
   * does not exist.
   */
  static Map<String, String> readConfigFile(Path path) throws IOException {
    Map<String, String> kv = new java.util.HashMap<>();
    if (!Files.exists(path)) {
      return kv;
    }
    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0) continue;
      String key = trimmed.substring(0, eq).trim();
      String rawValue = trimmed.substring(eq + 1).trim();
      // Strip optional surrounding double or single quotes.
      if (rawValue.length() >= 2) {
        char first = rawValue.charAt(0);
        char last = rawValue.charAt(rawValue.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
          rawValue = rawValue.substring(1, rawValue.length() - 1);
        }
      }
      kv.put(key, rawValue);
    }
    return kv;
  }
}
