package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.StorageStatus;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * FS1e1 — {@code shepard-admin storage status}. Read-only operator
 * visibility into the file-payload storage layer.
 *
 * <p>Reads {@code GET /v2/admin/storage} and surfaces the list of
 * discovered adapters with their enabled state and which one is
 * currently active. Replaces the FS1a placeholder that read the
 * MongoDB health check as a proxy.
 *
 * <p>Exit codes: 0 when an active provider is configured; 1 when
 * no provider is active or the endpoint fails.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the file-payload storage adapter status."
)
public final class StorageStatusCommand extends AbstractCommand {

  static final String STORAGE_PATH = "/v2/admin/storage";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    StorageStatus status;
    try {
      var response = client.get(STORAGE_PATH);
      status = ShepardHttpClient.mapper().readValue(response.body(), StorageStatus.class);
    } catch (Exception ex) {
      throw new AdminCliException("Could not read storage status from " + STORAGE_PATH + ": " + ex.getMessage(), ex);
    }

    String activeId = status.getActiveProviderId();
    boolean hasActive = activeId != null && !activeId.isBlank();

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(status));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise storage status to JSON: " + e.getMessage(), e);
      }
      return hasActive ? 0 : 1;
    }

    out().println("STORAGE — file-payload adapter status");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("active provider", hasActive ? activeId : "(none configured)");
    for (StorageStatus.Adapter adapter : status.getAdapters()) {
      String flags = (adapter.isEnabled() ? "enabled" : "disabled") +
        (adapter.isActive() ? ", active" : "");
      table.addRow("adapter: " + adapter.getId(), flags);
    }
    out().print(table.render());

    if (!hasActive) {
      out().println();
      out().println("note: set shepard.storage.provider=<id> in application.properties to activate an adapter");
    }

    return hasActive ? 0 : 1;
  }
}
