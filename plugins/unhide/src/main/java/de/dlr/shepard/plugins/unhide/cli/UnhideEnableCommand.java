package de.dlr.shepard.plugins.unhide.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.unhide.cli.io.UnhideConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin unhide enable} — UH1a.
 *
 * <p>Flips {@code :UnhideConfig.enabled=true} via RFC 7396
 * merge-patch.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the Unhide publish plugin (sets enabled=true)."
)
public final class UnhideEnableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    UnhideConfig cfg = buildClient()
      .patchJson(UnhideAdminPaths.CONFIG, Map.of("enabled", true), new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "Unhide enabled — feedPublic=" + cfg.isFeedPublic() + ", contactEmail=" + (cfg.getContactEmail() == null ? "(unset)" : cfg.getContactEmail())
    );
    return 0;
  }
}
