package de.dlr.shepard.plugins.unhide.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.unhide.cli.io.UnhideConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin unhide disable} — UH1a.
 *
 * <p>Flips {@code :UnhideConfig.enabled=false}. The feed endpoint
 * immediately returns 503 unhide.feed.disabled after this PATCH —
 * useful for emergency shut-off if a feed leak is suspected.
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the Unhide publish plugin (sets enabled=false). The feed returns 503 unhide.feed.disabled afterwards."
)
public final class UnhideDisableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    UnhideConfig cfg = buildClient()
      .patchJson(UnhideAdminPaths.CONFIG, Map.of("enabled", false), new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("Unhide disabled — feed will return 503 unhide.feed.disabled");
    return 0;
  }
}
