package de.dlr.shepard.cli.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the precedence ladder documented in
 * {@code aidocs/22-admin-cli-draft.md §3.4}:
 * <ol>
 *   <li>Explicit CLI flag</li>
 *   <li>Environment variable</li>
 *   <li>{@code ~/.shepard/admin.toml}</li>
 *   <li>Built-in default</li>
 * </ol>
 */
final class AdminConfigLoaderTest {

  @Test
  void defaultsApplyWhenNoSourcesSet(@TempDir Path home) throws IOException {
    AdminConfigLoader loader = new AdminConfigLoader(name -> null, home);

    AdminConfig config = loader.load(null, null);

    assertThat(config.getUrl()).isEqualTo(AdminConfig.DEFAULT_URL);
    assertThat(config.getApiKey()).isEmpty();
  }

  @Test
  void envOverridesDefault(@TempDir Path home) throws IOException {
    Map<String, String> env = new HashMap<>();
    env.put(AdminConfig.ENV_URL, "https://shepard.example.com");
    env.put(AdminConfig.ENV_API_KEY, "env-key");
    AdminConfigLoader loader = new AdminConfigLoader(env::get, home);

    AdminConfig config = loader.load(null, null);

    assertThat(config.getUrl()).isEqualTo("https://shepard.example.com");
    assertThat(config.getApiKey()).contains("env-key");
  }

  @Test
  void flagOverridesEnv(@TempDir Path home) throws IOException {
    Map<String, String> env = Map.of(
      AdminConfig.ENV_URL, "https://from-env",
      AdminConfig.ENV_API_KEY, "from-env-key"
    );
    AdminConfigLoader loader = new AdminConfigLoader(env::get, home);

    AdminConfig config = loader.load("https://from-flag", "from-flag-key");

    assertThat(config.getUrl()).isEqualTo("https://from-flag");
    assertThat(config.getApiKey()).contains("from-flag-key");
  }

  @Test
  void fileFillsInWhereEnvIsAbsent(@TempDir Path home) throws IOException {
    Path tomlDir = home.resolve(".shepard");
    Files.createDirectories(tomlDir);
    Files.writeString(
      tomlDir.resolve("admin.toml"),
      """
      # operator's friendly comment
      url = "https://from-file"
      apiKey = 'file-key'
      """
    );
    AdminConfigLoader loader = new AdminConfigLoader(name -> null, home);

    AdminConfig config = loader.load(null, null);

    assertThat(config.getUrl()).isEqualTo("https://from-file");
    assertThat(config.getApiKey()).contains("file-key");
  }

  @Test
  void envBeatsFile(@TempDir Path home) throws IOException {
    Path tomlDir = home.resolve(".shepard");
    Files.createDirectories(tomlDir);
    Files.writeString(
      tomlDir.resolve("admin.toml"),
      """
      url = "https://from-file"
      apiKey = "file-key"
      """
    );
    Map<String, String> env = Map.of(AdminConfig.ENV_URL, "https://from-env");
    AdminConfigLoader loader = new AdminConfigLoader(env::get, home);

    AdminConfig config = loader.load(null, null);

    assertThat(config.getUrl()).isEqualTo("https://from-env");
    // env didn't set the key — file value should still come through.
    assertThat(config.getApiKey()).contains("file-key");
  }

  @Test
  void blankFlagDoesNotShadow(@TempDir Path home) throws IOException {
    Map<String, String> env = Map.of(AdminConfig.ENV_URL, "https://from-env");
    AdminConfigLoader loader = new AdminConfigLoader(env::get, home);

    AdminConfig config = loader.load("", "  ");

    assertThat(config.getUrl()).isEqualTo("https://from-env");
    assertThat(config.getApiKey()).isEmpty();
  }

  @Test
  void missingFileIsNotAnError(@TempDir Path home) throws IOException {
    // No .shepard/admin.toml created.
    AdminConfigLoader loader = new AdminConfigLoader(name -> null, home);

    AdminConfig config = loader.load(null, null);

    assertThat(config.getUrl()).isEqualTo(AdminConfig.DEFAULT_URL);
  }

  @Test
  void readConfigFileIgnoresCommentsAndSectionHeaders(@TempDir Path home) throws IOException {
    Path file = home.resolve("admin.toml");
    Files.writeString(
      file,
      """
      # comment line
      [section-header-ignored]
      url = "https://parsed"
      not a key = value
      empty=
      """
    );

    Map<String, String> kv = AdminConfig.readConfigFile(file);

    assertThat(kv).containsEntry("url", "https://parsed");
    // The "not a key" line still has an `=` so it parses; that's fine,
    // it just doesn't shadow a real key. The TOML reader is forgiving
    // on purpose — see AdminConfig.readConfigFile javadoc.
  }
}
