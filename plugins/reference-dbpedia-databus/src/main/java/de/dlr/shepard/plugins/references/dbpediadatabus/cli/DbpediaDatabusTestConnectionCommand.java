package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin references dbpedia-databus test-connection} — REF1c.
 *
 * <p>Probes the configured {@code defaultEndpoint/api/info} endpoint.
 */
@Command(
  name = "test-connection",
  mixinStandardHelpOptions = true,
  description = "Probe the configured defaultEndpoint/api/info to check reachability."
)
public final class DbpediaDatabusTestConnectionCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    @SuppressWarnings("unchecked")
    Map<String, Object> result = buildClient()
      .postJson(DbpediaDatabusAdminPaths.TEST_CONNECTION, Map.of(), new TypeReference<Map<String, Object>>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise result to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    Object reachable = result.get("reachable");
    Object statusCode = result.get("statusCode");
    Object latencyMs = result.get("latencyMs");
    Object reason = result.get("reason");

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("reachable", String.valueOf(reachable));
    table.addRow("statusCode", statusCode == null ? "(none)" : String.valueOf(statusCode));
    table.addRow("latencyMs", latencyMs == null ? "(none)" : String.valueOf(latencyMs));
    table.addRow("reason", reason == null ? "(none)" : String.valueOf(reason));
    out().print(table.render());
    return Boolean.TRUE.equals(reachable) ? 0 : 1;
  }
}
