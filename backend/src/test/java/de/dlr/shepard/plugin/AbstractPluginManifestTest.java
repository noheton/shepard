package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Base test class asserting the structural contract of every
 * {@link PluginManifest}.
 *
 * <p>Extend this for each concrete plugin manifest and supply
 * {@link #manifest()} to get the cross-cutting assertions for free:
 *
 * <pre>{@code
 * class FooPluginManifestTest extends AbstractPluginManifestTest<FooPluginManifest> {
 *   @Override
 *   protected FooPluginManifest manifest() {
 *     return new FooPluginManifest();
 *   }
 * }
 * }</pre>
 *
 * <p>The assertions here are the minimum structural contract every
 * plugin must pass. Plugin-specific tests (exact id pin, sidecar
 * details, lifecycle hook behaviour) live in the extending class
 * and are not duplicated here.
 *
 * <p>This class is published via the backend {@code tests} classifier
 * JAR so plugin modules can consume it without duplicating it. Each
 * plugin pom declares:
 * <pre>{@code
 * <dependency>
 *   <groupId>de.dlr.shepard</groupId>
 *   <artifactId>backend</artifactId>
 *   <version>${revision}</version>
 *   <type>test-jar</type>
 *   <scope>test</scope>
 * </dependency>
 * }</pre>
 *
 * @param <T> concrete {@link PluginManifest} implementation under test
 */
public abstract class AbstractPluginManifestTest<T extends PluginManifest> {

  /**
   * Return the manifest under test. The instance is re-created for
   * each test via normal JUnit 5 per-method lifecycle, so this method
   * may be called multiple times — it should return a fresh or
   * reusable immutable instance (manifests are stateless by contract).
   */
  protected abstract T manifest();

  // ──────────────────────────────────────────────────────────────────────
  // id() contract
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void id_isNonBlank() {
    assertThat(manifest().id()).isNotBlank();
  }

  @Test
  void id_isLowercaseHyphenSeparated() {
    // "lowercase, hyphen-separated" per PluginManifest#id() Javadoc.
    // Pattern: starts with [a-z], followed by lowercase alphanum / hyphens.
    assertThat(manifest().id())
      .as("plugin id must be lowercase-hyphen-separated")
      .matches("[a-z][a-z0-9-]*");
  }

  // ──────────────────────────────────────────────────────────────────────
  // version() contract
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void version_isNonBlank() {
    assertThat(manifest().version()).isNotBlank();
  }

  // ──────────────────────────────────────────────────────────────────────
  // shepardCompatibility() contract
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void shepardCompatibility_isNonBlank() {
    assertThat(manifest().shepardCompatibility()).isNotBlank();
  }

  // ──────────────────────────────────────────────────────────────────────
  // sidecars() contract
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void sidecars_isNotNull() {
    assertThat(manifest().sidecars()).isNotNull();
  }

  @Test
  void sidecars_eachHasNonBlankId() {
    manifest()
      .sidecars()
      .forEach(
        s ->
          assertThat(s.id())
            .as("sidecar id for plugin '%s'", manifest().id())
            .isNotBlank()
      );
  }

  @Test
  void sidecars_eachHasNonBlankImage() {
    manifest()
      .sidecars()
      .forEach(
        s ->
          assertThat(s.image())
            .as("sidecar image for plugin '%s'", manifest().id())
            .isNotBlank()
      );
  }

  @Test
  void sidecars_eachHasNonBlankMemLimit() {
    manifest()
      .sidecars()
      .forEach(
        s ->
          assertThat(s.memLimit())
            .as("sidecar memLimit for plugin '%s'", manifest().id())
            .isNotBlank()
      );
  }
}
