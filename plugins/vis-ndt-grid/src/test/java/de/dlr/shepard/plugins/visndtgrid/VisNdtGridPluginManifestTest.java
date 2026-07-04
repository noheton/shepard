package de.dlr.shepard.plugins.visndtgrid;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * MFFD-RENDER-NDT-GRID slice 1 — structural and metadata smoke tests for
 * {@link VisNdtGridPluginManifest}.
 *
 * <p>The structural contract (id-format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is asserted by
 * {@link AbstractPluginManifestTest}. The tests below add the
 * plugin-specific checks:
 *
 * <ul>
 *   <li>id is the canonical {@code "vis-ndt-grid"}.</li>
 *   <li>title and description are non-blank and mention NDT.</li>
 *   <li>licence is {@code "Apache-2.0"}.</li>
 *   <li>The {@code NdtGridShape} SHACL resource is classpath-reachable at
 *       {@link VisNdtGridPluginManifest#SHAPE_RESOURCE} and contains the
 *       MFFD-specific predicates the validator expects.</li>
 *   <li>The SHAPE_IRI constant is stable (public API for the materialize
 *       dispatch key and the docs/reference page).</li>
 *   <li>Lifecycle hooks are safe to call with a {@code null} context.</li>
 * </ul>
 */
class VisNdtGridPluginManifestTest extends AbstractPluginManifestTest<VisNdtGridPluginManifest> {

  @Override
  protected VisNdtGridPluginManifest manifest() {
    return new VisNdtGridPluginManifest();
  }

  @Test
  void id_isVisNdtGrid() {
    assertThat(manifest().id()).isEqualTo("vis-ndt-grid");
  }

  @Test
  void title_isNonBlank() {
    assertThat(manifest().title()).isNotBlank();
  }

  @Test
  void title_mentionsNdt() {
    assertThat(manifest().title().toLowerCase()).contains("ndt");
  }

  @Test
  void description_isNonBlank() {
    assertThat(manifest().description()).isNotBlank();
  }

  @Test
  void description_mentionsMffdAndGrid() {
    String desc = manifest().description().toLowerCase();
    assertThat(desc).contains("ndt");
    assertThat(desc).contains("grid");
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
    // NDT grid renders in-browser (Canvas 2D / WebGL); no Python sidecar.
    // If a future server-side render path (e.g. large-format export service)
    // lands, update this test alongside the manifest.
    assertThat(manifest().sidecars()).isEmpty();
  }

  @Test
  void shapeResource_isClasspathReachable() {
    URL url = VisNdtGridPluginManifest.class.getResource(
      VisNdtGridPluginManifest.SHAPE_RESOURCE
    );
    assertThat(url)
      .as("NdtGridShape SHACL resource must be on the classpath at %s",
          VisNdtGridPluginManifest.SHAPE_RESOURCE)
      .isNotNull();
  }

  @Test
  void shapeResource_declaresNdtGridShape() throws Exception {
    URL url = VisNdtGridPluginManifest.class.getResource(
      VisNdtGridPluginManifest.SHAPE_RESOURCE
    );
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl)
      .contains("transform:NdtGridShape")
      .contains("sh:NodeShape")
      .contains("ndtgrid:collectionAppId")
      .contains("ndtgrid:colourMode")
      .contains("ndtgrid:colourMap")
      .contains("ndtgrid:layerFilter");
  }

  @Test
  void shapeResource_declaresColourModeIn() throws Exception {
    URL url = VisNdtGridPluginManifest.class.getResource(
      VisNdtGridPluginManifest.SHAPE_RESOURCE
    );
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    // Both supported colour modes must appear in the sh:in constraint.
    assertThat(ttl).contains("mean-delta-t").contains("pass-fail");
  }

  @Test
  void shapeIri_constantIsStable() {
    // The shape IRI is the materialize dispatch key and appears in the docs;
    // pin it so a refactor surfaces in the test bundle rather than breaking
    // the executor's supportedShapeIris() lookup silently.
    assertThat(VisNdtGridPluginManifest.SHAPE_IRI)
      .isEqualTo("http://semantics.dlr.de/shepard/transform#NdtGridShape");
  }

  @Test
  void shapeResourcePath_constantIsStable() {
    assertThat(VisNdtGridPluginManifest.SHAPE_RESOURCE)
      .isEqualTo("/shapes/ndt-grid.shacl.ttl");
  }

  @Test
  void onRegisterAndUnregister_doNotThrow_nullContext() {
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }
}
