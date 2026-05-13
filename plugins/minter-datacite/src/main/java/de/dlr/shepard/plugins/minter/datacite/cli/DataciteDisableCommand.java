package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite disable} — KIP1d.
 *
 * <p>Flips {@code :DataciteMinterConfig.enabled=false} via RFC 7396
 * merge-patch. Disabling makes {@code DataciteMinter.mint()} throw
 * {@code MinterException} immediately (no DataCite HTTP call); the
 * already-minted DOIs in DataCite remain — only future mints are
 * gated.
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the DataCite minter (sets enabled=false)."
)
public final class DataciteDisableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DataciteConfig cfg = buildClient()
      .patchJson(DataciteAdminPaths.CONFIG, Map.of("enabled", false), new TypeReference<DataciteConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("DataCite minter disabled — future mint calls throw publish.minter.failed until re-enabled.");
    return 0;
  }
}
