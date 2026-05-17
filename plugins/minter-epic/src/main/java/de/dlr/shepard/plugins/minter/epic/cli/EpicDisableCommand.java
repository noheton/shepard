package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters epic disable} — KIP1c.
 *
 * <p>Flips {@code :EpicMinterConfig.enabled=false} via RFC 7396
 * merge-patch. Disabling makes {@code EpicMinter.mint()} throw
 * {@code MinterException} immediately (no ePIC HTTP call).
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the ePIC minter (sets enabled=false)."
)
public final class EpicDisableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    EpicConfig cfg = buildClient()
      .patchJson(EpicAdminPaths.CONFIG, Map.of("enabled", false), new TypeReference<EpicConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("ePIC minter disabled — future mint calls throw publish.minter.failed until re-enabled.");
    return 0;
  }
}
