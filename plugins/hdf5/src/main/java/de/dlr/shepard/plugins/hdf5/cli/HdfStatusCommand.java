package de.dlr.shepard.plugins.hdf5.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.hdf5.cli.io.HdfCliConfig;
import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — {@code shepard-admin hdf status}.
 * Read-only view of the HDF plugin runtime configuration.
 *
 * <p>Fetches {@code GET /v2/admin/config/hdf} and prints the effective
 * {@code enabled} value.
 *
 * <p>Exit code: always 0 (status command; not a conditional gate).
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the current HDF5/HSDS plugin runtime configuration."
)
public final class HdfStatusCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/config/hdf";

  @Override
  protected Integer run() {
    HdfCliConfig cfg = buildClient().getJson(CONFIG_PATH, new TypeReference<HdfCliConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (Exception e) {
        throw new AdminCliException("Could not serialise HDF config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", cfg.getEnabled() != null ? Boolean.toString(cfg.getEnabled()) : "(default)");
    out().print(table.render());
    return 0;
  }
}
