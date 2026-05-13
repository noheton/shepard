package de.dlr.shepard.plugins.unhide.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.unhide.cli.io.UnhideConfig;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin unhide status} — UH1a.
 *
 * <p>Fetches and prints the runtime {@code :UnhideConfig} singleton.
 * Mirrors the shape returned by {@code GET /v2/admin/unhide/config}
 * — enabled / feedPublic / contactEmail / harvest-key fingerprint +
 * mintedAt timestamp.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current Unhide plugin runtime config."
)
public final class UnhideStatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    UnhideConfig cfg = buildClient().getJson(UnhideAdminPaths.CONFIG, new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(cfg.isEnabled()));
    table.addRow("feedPublic", Boolean.toString(cfg.isFeedPublic()));
    table.addRow("contactEmail", cfg.getContactEmail() == null ? "-" : cfg.getContactEmail());
    table.addRow(
      "harvestApiKey.fingerprint",
      cfg.getHarvestApiKeyFingerprint() == null ? "(no key)" : cfg.getHarvestApiKeyFingerprint()
    );
    table.addRow(
      "harvestApiKey.mintedAt",
      cfg.getHarvestApiKeyMintedAt() == null ? "(never)" : cfg.getHarvestApiKeyMintedAt().toString()
    );
    out().print(table.render());
    return 0;
  }
}
