package de.dlr.shepard.plugins.vistrace3d;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * VIS-T1 phase 1 — structural and metadata smoke tests for
 * {@link VisTrace3DPluginManifest}.
 *
 * <p>The structural contract (id-format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is asserted by
 * {@link AbstractPluginManifestTest}. The tests below add the
 * plugin-specific checks:
 *
 * <ul>
 *   <li>id is the canonical {@code "vis-trace3d"} (not a
 *       drift-prone duplicate).</li>
 *   <li>title and description are non-blank (PM1c metadata fields).</li>
 *   <li>licence is {@code "Apache-2.0"}.</li>
 *   <li>repository URL is non-empty.</li>
 *   <li>The {@code Trace3DViewShape} SHACL resource is reachable on
 *       the classpath at {@link VisTrace3DPluginManifest#SHAPE_RESOURCE}
 *       and contains the trace3d-specific predicates the
 *       {@code POST /v2/shapes/validate} consumer expects.</li>
 *   <li>The lifecycle hooks ({@code onRegister} / {@code onUnregister})
 *       are safe to call with a {@code null} context (phase-1 shape;
 *       Quarkus CDI hot-reload may invoke them more than once).</li>
 * </ul>
 */
class VisTrace3DPluginManifestTest extends AbstractPluginManifestTest<VisTrace3DPluginManifest> {

  @Override
  protected VisTrace3DPluginManifest manifest() {
    return new VisTrace3DPluginManifest();
  }

  @Test
  void id_isVisTrace3D() {
    assertThat(manifest().id()).isEqualTo("vis-trace3d");
  }

  @Test
  void title_isNonBlank() {
    assertThat(manifest().title()).isNotBlank();
  }

  @Test
  void description_isNonBlank() {
    assertThat(manifest().description()).isNotBlank();
  }

  @Test
  void description_mentionsTrace3D() {
    // catches accidental copy-paste of another plugin's description
    assertThat(manifest().description().toLowerCase())
      .contains("trace");
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
    // Phase 1 lands on the post-fork v6 codebase; the compat string
    // must include 6.x. If a future migration moves it to 7+, this
    // test must be updated alongside the manifest.
    assertThat(manifest().shepardCompatibility()).contains("6");
  }

  @Test
  void sidecars_isEmpty_browserRenderedNoBackendDeps() {
    // Trace3D renders entirely in the browser (Three.js / Line2);
    // there is no Python sidecar in phase 1. If the future
    // server-side-render escape hatch (Isaac WebRTC, VIS-S3 stretch)
    // lands, this test must be updated to assert the new sidecar.
    assertThat(manifest().sidecars()).isEmpty();
  }

  @Test
  void shapeResource_isClasspathReachable() {
    URL url = VisTrace3DPluginManifest.class.getResource(
      VisTrace3DPluginManifest.SHAPE_RESOURCE
    );
    assertThat(url)
      .as("Trace3DViewShape SHACL resource must be on the classpath")
      .isNotNull();
  }

  @Test
  void shapeResource_declaresTrace3DViewShape() throws Exception {
    URL url = VisTrace3DPluginManifest.class.getResource(
      VisTrace3DPluginManifest.SHAPE_RESOURCE
    );
    assertThat(url).isNotNull();
    String ttl = new String(url.openStream().readAllBytes());
    assertThat(ttl)
      .contains("trace3d:Trace3DViewShape")
      .contains("sh:NodeShape")
      .contains("shepard-ui:hasChannelBinding")
      .contains("trace3d:colorMap")
      .contains("trace3d:interpolation")
      .contains("trace3d:alignment");
  }

  @Test
  void shapeResource_constantPathIsStable() {
    // The shape resource path is a public API for the validate
    // endpoint loader and the docs/reference page; pin it so a
    // refactor that moves the file surfaces in the test bundle
    // rather than silently breaking the consumer.
    assertThat(VisTrace3DPluginManifest.SHAPE_RESOURCE)
      .isEqualTo("/shapes/trace-3d-view.shacl.ttl");
  }

  @Test
  void onRegisterAndUnregister_doNotThrow_nullContext() {
    // Phase-1 lifecycle is a log-and-return — both must be safe to
    // call with a null context (the registry guarantees a non-null
    // ctx in production, but defensive callers and the future
    // hot-reload code path will exercise nullable shapes).
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }
}
