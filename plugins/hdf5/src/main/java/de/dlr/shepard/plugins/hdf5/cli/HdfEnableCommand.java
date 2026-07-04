package de.dlr.shepard.plugins.hdf5.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.hdf5.cli.io.HdfCliConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — {@code shepard-admin hdf enable}.
 * Flips {@code :HdfConfig.enabled=true} via RFC 7396 merge-patch on
 * {@code PATCH /v2/admin/config/hdf}.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the HDF5/HSDS plugin at runtime (sets enabled=true)."
)
public final class HdfEnableCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/config/hdf";

  @Override
  protected Integer run() {
    HdfCliConfig cfg = buildClient()
      .patchJson(CONFIG_PATH, Map.of("enabled", true), new TypeReference<HdfCliConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (Exception e) {
        throw new AdminCliException("Could not serialise HDF config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("HDF — enabled");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", cfg.getEnabled() != null ? Boolean.toString(cfg.getEnabled()) : "(default)");
    out().print(table.render());
    return 0;
  }
}
