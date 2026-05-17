package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters epic enable} — KIP1c.
 *
 * <p>Flips {@code :EpicMinterConfig.enabled=true} via RFC 7396
 * merge-patch.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the ePIC minter (sets enabled=true)."
)
public final class EpicEnableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    EpicConfig cfg = buildClient()
      .patchJson(EpicAdminPaths.CONFIG, Map.of("enabled", true), new TypeReference<EpicConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "ePIC minter enabled — apiBaseUrl=" + (cfg.getApiBaseUrl() == null ? "(unset)" : cfg.getApiBaseUrl()) +
      ", handlePrefix=" + (cfg.getHandlePrefix() == null ? "(unset)" : cfg.getHandlePrefix()) +
      ", credentialSet=" + cfg.isCredentialSet()
    );
    if (!cfg.isCredentialSet()) {
      err().println(
        "warning: credentialSet=false — mint will fail until you run `shepard-admin minters epic set-credential`"
      );
    }
    if (cfg.getHandlePrefix() == null || cfg.getHandlePrefix().isBlank()) {
      err().println("warning: handlePrefix unset — set via `shepard-admin minters epic set-prefix <prefix>`");
    }
    return 0;
  }
}
