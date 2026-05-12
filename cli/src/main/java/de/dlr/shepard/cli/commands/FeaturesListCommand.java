package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.FeatureToggle;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.List;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin features list} — calls
 * {@code GET /v2/admin/features} and prints the toggle catalogue.
 *
 * <p>Wire shape mirrors {@code FeatureToggleIO} on the backend:
 * {@code [{ name, enabled, description }]}.
 *
 * <p>Exit codes: 0 on success; 1 on any operator-readable error
 * (connect failure, 401/403, malformed body); 2 on unexpected
 * runtime exception.
 */
@Command(
  name = "list",
  description = "List runtime feature toggles and their current state."
)
public final class FeaturesListCommand extends AbstractCommand {

  static final String PATH = "/v2/admin/features";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();
    List<FeatureToggle> toggles = client.getJson(PATH, new TypeReference<List<FeatureToggle>>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(toggles));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise features to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("NAME", "ENABLED", "DESCRIPTION");
    for (FeatureToggle toggle : toggles) {
      table.addRow(
        toggle.getName(),
        toggle.isEnabled() ? "true" : "false",
        toggle.getDescription()
      );
    }
    out().print(table.render());
    return 0;
  }
}
