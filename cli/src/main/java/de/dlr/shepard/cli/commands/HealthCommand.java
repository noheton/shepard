package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.HealthCheckResult;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin health} — probes the backend's readiness
 * and liveness endpoints and prints a per-DB summary table.
 *
 * <p>Exit codes: 0 when both readiness and liveness report
 * {@code UP}; 1 when either is {@code DOWN} (so the command is
 * usable as a kubelet-style probe in a shell pipeline).
 *
 * <p>Note: the shepard backend serves the SmallRye-Health endpoints
 * under {@code /shepard/api/healthz/ready} and
 * {@code /shepard/api/healthz/live} per
 * {@code quarkus.smallrye-health.root-path} in
 * {@code application.properties}.
 */
@Command(
  name = "health",
  description = "Show per-database readiness + liveness state."
)
public final class HealthCommand extends AbstractCommand {

  static final String READINESS_PATH = "/shepard/api/healthz/ready";
  static final String LIVENESS_PATH = "/shepard/api/healthz/live";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    HealthCheckResult readiness = fetch(client, READINESS_PATH);
    HealthCheckResult liveness = fetch(client, LIVENESS_PATH);

    if (wantsJson()) {
      try {
        ObjectNode root = jsonMapper().createObjectNode();
        root.set("readiness", jsonMapper().valueToTree(readiness));
        root.set("liveness", jsonMapper().valueToTree(liveness));
        out().println(jsonMapper().writeValueAsString(root));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise health to JSON: " + e.getMessage(), e);
      }
    } else {
      renderTable("READINESS", readiness);
      out().println();
      renderTable("LIVENESS", liveness);
      out().println();
      out().println("overall: readiness=" + readiness.getStatus() + " liveness=" + liveness.getStatus());
    }

    return (readiness.isUp() && liveness.isUp()) ? 0 : 1;
  }

  /**
   * Quarkus returns HTTP 200 on UP and 503 on DOWN. Both bodies
   * are still valid JSON in the SmallRye-Health envelope shape,
   * so the 503 path is treated as a successful read rather than
   * an error.
   */
  private HealthCheckResult fetch(ShepardHttpClient client, String path) {
    try {
      return client.getJson(path, new TypeReference<HealthCheckResult>() {});
    } catch (AdminCliException e) {
      // Quarkus returns 503 with a JSON body on DOWN — re-attempt
      // by raw-fetching and parsing the body directly. Anything
      // else (connect refusal, 404, malformed body) propagates.
      if (e.getMessage() != null && e.getMessage().contains("HTTP 503")) {
        var response = client.get(path);
        try {
          return ShepardHttpClient.mapper().readValue(response.body(), HealthCheckResult.class);
        } catch (Exception ex) {
          throw new AdminCliException("Could not parse 503 health body from " + path + ": " + ex.getMessage(), ex);
        }
      }
      throw e;
    }
  }

  private void renderTable(String header, HealthCheckResult result) {
    out().println(header + " — overall " + result.getStatus());
    TableFormatter table = new TableFormatter("CHECK", "STATUS", "DATA");
    for (HealthCheckResult.Check check : result.getChecks()) {
      table.addRow(check.getName(), check.getStatus(), renderData(check.getData()));
    }
    out().print(table.render());
  }

  private static String renderData(Map<String, Object> data) {
    if (data == null || data.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, Object> e : data.entrySet()) {
      if (!first) sb.append(", ");
      first = false;
      sb.append(e.getKey()).append('=').append(e.getValue());
    }
    return sb.toString();
  }
}
