package de.dlr.shepard.plugins.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * IMP1a — smoke tests for the PluginManifest scaffold. We test:
 *
 * <ul>
 *   <li>the manifest declares the right id / version / compat
 *       range — these strings are part of the contract surface
 *       PluginRegistry uses to drive the admin REST + CLI list;
 *   <li>the ServiceLoader SPI file resolves to this class — guards
 *       against a typo in
 *       {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest};
 *   <li>{@code onRegister} / {@code onUnregister} are no-op
 *       safe — when PR-2 grows the bean wiring this test catches
 *       a regression where a thrown exception would land the
 *       plugin in FAILED state at startup;
 *   <li>the metadata fields (title, description, urls, licence)
 *       are non-null and non-empty — these are what an operator
 *       sees in {@code shepard-admin plugins list}.
 * </ul>
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
final class ImporterPluginManifestTest extends AbstractPluginManifestTest<ImporterPluginManifest> {

  @Override
  protected ImporterPluginManifest manifest() {
    return new ImporterPluginManifest();
  }

  @Test
  void declares_expected_plugin_id_and_version() {
    assertThat(manifest().id()).isEqualTo("importer");
    assertThat(manifest().version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(manifest().shepardCompatibility()).isEqualTo(">=6.0.0-SNAPSHOT,<7");
  }

  @Test
  void surfaces_operator_metadata() {
    assertThat(manifest().title()).contains("Importer");
    assertThat(manifest().description()).isNotBlank();
    assertThat(manifest().homepageUrl()).isPresent();
    assertThat(manifest().repositoryUrl()).isPresent();
    assertThat(manifest().licence()).isEqualTo("Apache-2.0");
    assertThat(manifest().dependencies()).isEmpty();
  }

  @Test
  void on_register_throws_in_prod_without_secret() {
    // IMPL3 — the guard means onRegister is no longer unconditionally
    // safe. In the test JVM there is no Quarkus context, so
    // ConfigProvider returns the empty-string default for the secret
    // and no profile, defaulting to "prod" for both. The guard fires
    // and the plugin correctly lands in FAILED state.  PluginRegistry
    // catches this RuntimeException and keeps the app running — the
    // fail-soft contract still holds at the registry level.
    var ctx = Mockito.mock(PluginContext.class);
    assertThatThrownBy(() -> manifest().onRegister(ctx))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SHEPARD_INSTANCE_SECRET is insecure");
    // onUnregister remains unconditionally safe.
    manifest().onUnregister(ctx);
  }

  @Test
  void service_loader_resolves_to_this_manifest() {
    // Guards against a typo in
    // META-INF/services/de.dlr.shepard.plugin.PluginManifest.
    boolean found = false;
    for (PluginManifest pm : ServiceLoader.load(PluginManifest.class)) {
      if (pm instanceof ImporterPluginManifest) {
        found = true;
        break;
      }
    }
    assertThat(found)
      .as("ImporterPluginManifest must be discoverable via ServiceLoader")
      .isTrue();
  }

  // ====================== IMPL3 credential guard ======================

  /**
   * IMPL3 — in prod mode, a blank / default / too-short secret must
   * throw so the plugin lands FAILED rather than running silently with
   * an insecure credential.
   */
  @Test
  void startup_withDefaultCredential_inProdProfile_throwsIllegalState() {
    assertThatThrownBy(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("prod", "changeme")
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SHEPARD_INSTANCE_SECRET is insecure");
  }

  @Test
  void startup_withBlankCredential_inProdProfile_throwsIllegalState() {
    assertThatThrownBy(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("prod", "")
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SHEPARD_INSTANCE_SECRET is insecure");
  }

  @Test
  void startup_withShortCredential_inProdProfile_throwsIllegalState() {
    // 15 chars — one below the 16-char floor
    assertThatThrownBy(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("prod", "tooshort1234567")
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SHEPARD_INSTANCE_SECRET is insecure");
  }

  @Test
  void startup_withStrongCredential_inProdProfile_succeeds() {
    // 32 hex chars — well above the 16-char floor
    assertThatCode(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("prod", "a1b2c3d4e5f60708a1b2c3d4e5f60708")
    ).doesNotThrowAnyException();
  }

  @Test
  void startup_withDefaultCredential_inDevProfile_succeeds() {
    // Dev profile must never be blocked — local dev doesn't set a strong secret
    assertThatCode(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("dev", "changeme")
    ).doesNotThrowAnyException();
  }

  @Test
  void startup_withDefaultCredential_inTestProfile_succeeds() {
    // Test profile must never be blocked — CI doesn't set a strong secret
    assertThatCode(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("test", "changeme")
    ).doesNotThrowAnyException();
  }

  @Test
  void startup_withChangemeCaseVariant_inProdProfile_throwsIllegalState() {
    // Case-insensitive match on "changeme" — an operator who copies
    // the doc literally may accidentally use "Changeme" or "CHANGEME".
    assertThatThrownBy(() ->
      ImporterPluginManifest.enforceInstanceSecretGuard("prod", "CHANGEME")
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SHEPARD_INSTANCE_SECRET is insecure");
  }
}
