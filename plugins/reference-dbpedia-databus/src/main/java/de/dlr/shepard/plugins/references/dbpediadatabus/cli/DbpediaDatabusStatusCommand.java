package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.references.dbpediadatabus.cli.io.DbpediaDatabusConfig;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin references dbpedia-databus status} — REF1c.
 *
 * <p>Fetches and prints the runtime {@code :DbpediaDatabusConfig} singleton.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current DBpedia Databus plugin runtime config."
)
public final class DbpediaDatabusStatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DbpediaDatabusConfig cfg = buildClient().getJson(DbpediaDatabusAdminPaths.CONFIG, new TypeReference<DbpediaDatabusConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(cfg.isEnabled()));
    table.addRow("defaultEndpoint", cfg.getDefaultEndpoint() == null ? "(unset)" : cfg.getDefaultEndpoint());
    table.addRow("cacheTtlSeconds", Long.toString(cfg.getCacheTtlSeconds()));
    table.addRow("authMode", cfg.getAuthMode() == null ? "none" : cfg.getAuthMode());
    table.addRow("oauthTokenUrl", cfg.getOauthTokenUrl() == null ? "(unset)" : cfg.getOauthTokenUrl());
    table.addRow("oauthClientId", cfg.getOauthClientId() == null ? "(unset)" : cfg.getOauthClientId());
    table.addRow("oauthClientSecretSet", Boolean.toString(cfg.isOauthClientSecretSet()));
    table.addRow(
      "oauthClientSecret.fingerprint",
      cfg.getOauthClientSecretFingerprint() == null ? "(no secret)" : cfg.getOauthClientSecretFingerprint()
    );
    table.addRow("updatedBy", cfg.getUpdatedBy() == null ? "(never)" : cfg.getUpdatedBy());
    table.addRow("updatedAt", cfg.getUpdatedAt() == null ? "(never)" : cfg.getUpdatedAt().toString());
    out().print(table.render());
    return 0;
  }
}
