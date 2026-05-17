package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters epic status} — KIP1c.
 *
 * <p>Fetches and prints the runtime {@code :EpicMinterConfig}
 * singleton.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current ePIC minter runtime config."
)
public final class EpicStatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    EpicConfig cfg = buildClient().getJson(EpicAdminPaths.CONFIG, new TypeReference<EpicConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(cfg.isEnabled()));
    table.addRow("apiBaseUrl", nv(cfg.getApiBaseUrl()));
    table.addRow("handlePrefix", nv(cfg.getHandlePrefix()));
    table.addRow("credentialSet", Boolean.toString(cfg.isCredentialSet()));
    table.addRow(
      "credential.fingerprint",
      cfg.getCredentialFingerprint() == null ? "(no credential)" : cfg.getCredentialFingerprint()
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
