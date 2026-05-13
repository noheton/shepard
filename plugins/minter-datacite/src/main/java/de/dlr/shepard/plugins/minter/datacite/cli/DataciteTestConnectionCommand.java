package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteTestConnection;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite test-connection} — KIP1d.
 *
 * <p>Issues a {@code POST /v2/admin/minters/datacite/test-connection}
 * which has the server probe {@code <apiBaseUrl>/heartbeat} and
 * reports {@code reachable / statusCode / latencyMs}. Useful before
 * flipping {@code enabled=true}.
 *
 * <p>Exit code: 0 on reachable, 1 on unreachable (so CI scripts can
 * fail fast on a misconfigured deployment).
 */
@Command(
  name = "test-connection",
  mixinStandardHelpOptions = true,
  description = "Probe the configured DataCite API. Exits 0 on reachable, 1 on unreachable."
)
public final class DataciteTestConnectionCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DataciteTestConnection result = buildClient()
      .postJson(DataciteAdminPaths.TEST_CONNECTION, null, new TypeReference<DataciteTestConnection>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise test-connection response to JSON: " + e.getMessage(), e);
      }
      return result.isReachable() ? 0 : 1;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("reachable", Boolean.toString(result.isReachable()));
    table.addRow("statusCode", Integer.toString(result.getStatusCode()));
    table.addRow("latencyMs", Long.toString(result.getLatencyMs()));
    table.addRow("apiBaseUrl", result.getApiBaseUrl() == null ? "(unset)" : result.getApiBaseUrl());
    if (result.getDetail() != null) {
      table.addRow("detail", result.getDetail());
    }
    out().print(table.render());
    return result.isReachable() ? 0 : 1;
  }
}
