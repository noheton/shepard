package de.dlr.shepard.plugins.visafpthermo;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 1 — structural and metadata smoke tests for
 * {@link VisAfpThermoOverlayPluginManifest}.
 *
 * <p>The structural contract (id-format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is asserted by
 * {@link AbstractPluginManifestTest}. The tests below add the
 * plugin-specific checks:
 *
 * <ul>
 *   <li>id is the canonical {@code "vis-afp-thermo-overlay"}.</li>
 *   <li>title and description are non-blank and mention AFP and NDT.</li>
 *   <li>licence is {@code "Apache-2.0"}.</li>
 *   <li>The {@code AfpThermoOverlayShape} SHACL resource is classpath-reachable at
 *       {@link VisAfpThermoOverlayPluginManifest#SHAPE_RESOURCE} and contains the
 *       MFFD-specific predicates the validator expects.</li>
 *   <li>The SHAPE_IRI constant is stable (public API for the materialize
 *       dispatch key and the docs/reference page).</li>
 *   <li>Lifecycle hooks are safe to call with a {@code null} context.</li>
 * </ul>
 */
class VisAfpThermoOverlayPluginManifestTest
    extends AbstractPluginManifestTest<VisAfpThermoOverlayPluginManifest> {

  @Override
  protected VisAfpThermoOverlayPluginManifest manifest() {
    return new VisAfpThermoOverlayPluginManifest();
  }

  @Test
  void id_isVisAfpThermoOverlay() {
    assertThat(manifest().id()).isEqualTo("vis-afp-thermo-overlay");
  }

  @Test
  void title_isNonBlank() {
    assertThat(manifest().title()).isNotBlank();
  }

  @Test
  void title_mentionsAfpAndNdt() {
    String title = manifest().title().toLowerCase();
    assertThat(title).contains("afp");
    assertThat(title).contains("ndt");
  }

  @Test
  void description_isNonBlank() {
    assertThat(manifest().description()).isNotBlank();
  }

  @Test
  void description_mentionsMffdAndOverlay() {
    String desc = manifest().description().toLowerCase();
    assertThat(desc).contains("afp");
    assertThat(desc).contains("ndt");
    assertThat(desc).contains("overlay");
  }

  @Test
  void licence_isApache2() {
    assertThat(manifest().licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void repositoryUrl_isPresent() {
    assertThat(manifest().repositoryUrl()).isPresent();
  }

  @Test
  void shepardCompatibility_targetsV6() {
    assertThat(manifest().shepardCompatibility()).contains("6");
  }

  @Test
  void sidecars_isEmpty_browserRenderedNoBackendDeps() {
    assertThat(manifest().sidecars()).isEmpty();
  }

  @Test
  void shapeResource_isClasspathReachable() {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url)
        .as("SHACL shape TTL must be on the classpath at " + VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE)
        .isNotNull();
  }

  @Test
  void shapeResource_containsAfpThermoOverlayShapeIri() throws Exception {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl).contains("AfpThermoOverlayShape");
  }

  @Test
  void shapeResource_containsAfpDataObjectBinding() throws Exception {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl).contains("afpDataObjectAppId");
  }

  @Test
  void shapeResource_containsNdtDataObjectBinding() throws Exception {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl).contains("ndtDataObjectAppId");
  }

  @Test
  void shapeResource_containsTcpChannelShIn() throws Exception {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl).contains("tcp-temperature");
    assertThat(ttl).contains("consolidation-force");
  }

  @Test
  void shapeResource_containsSyncModeShIn() throws Exception {
    URL url =
        VisAfpThermoOverlayPluginManifest.class.getResource(
            VisAfpThermoOverlayPluginManifest.SHAPE_RESOURCE);
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl).contains("side-by-side");
    assertThat(ttl).contains("overlay");
    assertThat(ttl).contains("split");
  }

  @Test
  void shapeIri_isStable() {
    assertThat(VisAfpThermoOverlayPluginManifest.SHAPE_IRI)
        .isEqualTo("http://semantics.dlr.de/shepard/transform#AfpThermoOverlayShape");
  }

  @Test
  void onRegister_safeWithNullContext() {
    manifest().onRegister(null);
  }

  @Test
  void onUnregister_safeWithNullContext() {
    manifest().onUnregister(null);
  }
}
