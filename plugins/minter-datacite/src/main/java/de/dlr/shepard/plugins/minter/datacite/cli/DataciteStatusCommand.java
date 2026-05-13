package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite status} — KIP1d.
 *
 * <p>Fetches and prints the runtime {@code :DataciteMinterConfig}
 * singleton.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current DataCite minter runtime config."
)
public final class DataciteStatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DataciteConfig cfg = buildClient().getJson(DataciteAdminPaths.CONFIG, new TypeReference<DataciteConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(cfg.isEnabled()));
    table.addRow("apiBaseUrl", nv(cfg.getApiBaseUrl()));
    table.addRow("handlePrefix", nv(cfg.getHandlePrefix()));
    table.addRow("repositoryId", nv(cfg.getRepositoryId()));
    table.addRow("publisher", nv(cfg.getPublisher()));
    table.addRow("landingPageBase", nv(cfg.getLandingPageBase()));
    table.addRow("defaultState", nv(cfg.getDefaultState()));
    table.addRow("passwordSet", Boolean.toString(cfg.isPasswordSet()));
    table.addRow(
      "password.fingerprint",
      cfg.getPasswordFingerprint() == null ? "(no credential)" : cfg.getPasswordFingerprint()
    );
    table.addRow("updatedAt", cfg.getUpdatedAt() == null ? "(never)" : cfg.getUpdatedAt().toString());
    table.addRow("updatedBy", nv(cfg.getUpdatedBy()));
    out().print(table.render());
    return 0;
  }

  private static String nv(String s) {
    return (s == null || s.isBlank()) ? "-" : s;
  }
}
