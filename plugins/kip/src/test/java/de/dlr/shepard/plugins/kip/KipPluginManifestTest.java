package de.dlr.shepard.plugins.kip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.plugins.kip.resources.KipResolverRest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PM1g — verifies that {@link KipPluginManifest} self-declares its
 * public-path prefix via {@link de.dlr.shepard.plugin.PluginManifest#publicPathPrefixes()}.
 */
class KipPluginManifestTest {

  private final KipPluginManifest manifest = new KipPluginManifest();

  @Test
  void publicPathPrefixes_returnsKipResolverPrefix() {
    List<String> prefixes = manifest.publicPathPrefixes();
    assertEquals(1, prefixes.size());
    assertEquals(KipResolverRest.PUBLIC_PATH_PREFIX, prefixes.get(0));
  }

  @Test
  void publicPaths_returnsEmpty() {
    // The KIP plugin uses a prefix (variable PID suffix), not an exact path.
    assertTrue(manifest.publicPaths().isEmpty());
  }
}
