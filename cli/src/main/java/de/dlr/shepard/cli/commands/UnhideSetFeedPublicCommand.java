package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.UnhideConfig;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin unhide set-feed-public <true|false>} — UH1a.
 *
 * <p>Flips {@code :UnhideConfig.feedPublic}. {@code true} makes the
 * feed reachable without authentication; {@code false} (default)
 * requires an X-API-KEY matching the harvest hash.
 */
@Command(
  name = "set-feed-public",
  mixinStandardHelpOptions = true,
  description = "Set feedPublic to true (unauthenticated feed) or false (X-API-KEY required)."
)
public final class UnhideSetFeedPublicCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    paramLabel = "<true|false>",
    description = "Whether the feed is reachable without authentication."
  )
  boolean value;

  @Override
  protected Integer run() {
    UnhideConfig cfg = buildClient()
      .patchJson(UnhideAdminPaths.CONFIG, Map.of("feedPublic", value), new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("feedPublic = " + cfg.isFeedPublic());
    return 0;
  }
}
