package de.dlr.shepard.plugins.unhide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.plugins.unhide.resources.UnhideFeedRest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PM1g — verifies that {@link UnhidePluginManifest} self-declares its
 * public-path via {@link de.dlr.shepard.plugin.PluginManifest#publicPaths()}.
 */
class UnhidePluginManifestTest {

  private final UnhidePluginManifest manifest = new UnhidePluginManifest();

  @Test
  void publicPaths_returnsHarvestFeedPath() {
    List<String> paths = manifest.publicPaths();
    assertEquals(1, paths.size());
    assertEquals(UnhideFeedRest.PUBLIC_FEED_PATH, paths.get(0));
  }

  @Test
  void publicPathPrefixes_returnsEmpty() {
    // The Unhide feed is an exact path, not a prefix — no variable suffix.
    assertTrue(manifest.publicPathPrefixes().isEmpty());
  }
}
