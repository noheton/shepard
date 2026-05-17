package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.InstanceRorConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * ROR1 — {@code shepard-admin instance ror status}. Read-only
 * visibility into the instance-level Research Organization Registry
 * configuration.
 *
 * <p>Reads {@code GET /v2/admin/instance/ror} and surfaces the
 * {@code rorId}, {@code organizationName}, and computed
 * {@code rorUrl}. Exit code 0 when a ROR ID is configured; 1 when
 * not.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the instance-level ROR (Research Organization Registry) configuration."
)
public final class InstanceRorStatusCommand extends AbstractCommand {

  static final String ROR_PATH = "/v2/admin/instance/ror";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    var response = client.get(ROR_PATH);
    String body = response.body();

    InstanceRorConfig config;
    try {
      config = ShepardHttpClient.mapper().readValue(body, InstanceRorConfig.class);
    } catch (Exception ex) {
      throw new AdminCliException("Could not parse ROR config response: " + ex.getMessage(), ex);
    }

    boolean hasRorId = config.getRorId() != null && !config.getRorId().isBlank();

    if (wantsJson()) {
      out().println(body);
      return hasRorId ? 0 : 1;
    }

    out().println("INSTANCE ROR — Research Organization Registry configuration");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("rorId", config.getRorId() != null ? config.getRorId() : "(not set)");
    table.addRow("organizationName", config.getOrganizationName() != null ? config.getOrganizationName() : "(not set)");
    table.addRow("rorUrl", config.getRorUrl() != null ? config.getRorUrl() : "(not set)");
    out().print(table.render());

    if (!hasRorId) {
      out().println();
      out().println("note: use 'shepard-admin instance ror set --ror-id <id>' to configure the institution's ROR ID.");
    }

    return hasRorId ? 0 : 1;
  }
}
