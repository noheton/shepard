package de.dlr.shepard.cli.config;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves an {@link AdminConfig} from the layered sources in
 * §3.4 of {@code aidocs/22-admin-cli-draft.md}, precedence-ordered:
 * <ol>
 *   <li>Explicit CLI flag values (highest priority).</li>
 *   <li>Environment variables
 *       ({@code SHEPARD_ADMIN_URL}, {@code SHEPARD_ADMIN_API_KEY}).</li>
 *   <li>{@code ~/.shepard/admin.toml}.</li>
 *   <li>Built-in defaults — URL falls back to
 *       {@link AdminConfig#DEFAULT_URL}; API key stays
 *       {@code null} (commands needing auth fail with a
 *       human-readable message).</li>
 * </ol>
 *
 * <p>This class is intentionally pure-data so tests can swap the
 * env-var lookup and the user-home path. Public callers usually
 * just invoke {@link #load(String, String)}.
 */
public final class AdminConfigLoader {

  private final Function<String, String> env;
  private final Path userHome;

  public AdminConfigLoader() {
    this(System::getenv, Paths.get(System.getProperty("user.home", ".")));
  }

  /** Test seam: inject env-var lookup and user-home path. */
  public AdminConfigLoader(Function<String, String> env, Path userHome) {
    this.env = env;
    this.userHome = userHome;
  }

  /**
   * Resolve effective URL + API key from the layered sources.
   * Either parameter may be {@code null} (meaning "no CLI override").
   */
  public AdminConfig load(String urlFlag, String apiKeyFlag) throws IOException {
    Map<String, String> file = AdminConfig.readConfigFile(userHome.resolve(AdminConfig.DEFAULT_CONFIG_PATH));

    String url = firstNonBlank(urlFlag, env.apply(AdminConfig.ENV_URL), file.get("url"), AdminConfig.DEFAULT_URL);
    String apiKey = firstNonBlank(apiKeyFlag, env.apply(AdminConfig.ENV_API_KEY), file.get("apiKey"));

    return new AdminConfig(url, apiKey);
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
