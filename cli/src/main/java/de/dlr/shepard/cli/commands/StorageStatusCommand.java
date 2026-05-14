package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.HealthCheckResult;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * FS1a — {@code shepard-admin storage status}. Read-only operator
 * visibility into the file-payload storage layer.
 *
 * <p>FS1a scope (no admin REST surface yet — that's FS1d): the
 * command reads the existing {@code /shepard/api/healthz/ready}
 * endpoint and surfaces the {@code MongoDB Health Check} entry as
 * the proxy for "is the default GridFS storage adapter healthy?"
 * The active provider id itself is not yet readable from the wire —
 * an operator who needs that flag value can grep
 * {@code shepard.storage.provider} in {@code application.properties}
 * or the backend startup log. FS1d (the
 * {@code GET /v2/admin/storage} endpoint) replaces this with a
 * proper status payload + per-adapter detail.
 *
 * <p>Exit codes: 0 when MongoDB reports {@code UP}; 1 when
 * {@code DOWN} or the readiness probe itself fails. Useful as a
 * kubelet-style probe in a shell pipeline.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the file-payload storage adapter status."
)
public final class StorageStatusCommand extends AbstractCommand {

  static final String READINESS_PATH = "/shepard/api/healthz/ready";
  static final String MONGO_CHECK_NAME = "MongoDB Health Check";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    HealthCheckResult readiness;
    try {
      var response = client.get(READINESS_PATH, true);
      readiness = ShepardHttpClient.mapper().readValue(response.body(), HealthCheckResult.class);
    } catch (Exception ex) {
      throw new AdminCliException("Could not read readiness from " + READINESS_PATH + ": " + ex.getMessage(), ex);
    }

    HealthCheckResult.Check mongoCheck = findMongoCheck(readiness);
    String mongoStatus = mongoCheck != null ? mongoCheck.getStatus() : "UNKNOWN";
    boolean mongoUp = mongoCheck != null && mongoCheck.isUp();

    if (wantsJson()) {
      try {
        ObjectNode root = jsonMapper().createObjectNode();
        // FS1a posture: surface what we *can* read from the wire.
        // The active provider id itself is not yet exposed (FS1d
        // territory). Operators wanting it grep
        // application.properties on the backend host.
        root.put("activeProviderHint", "see shepard.storage.provider in application.properties (FS1d will expose it)");
        root.put("gridfsConnection", mongoStatus);
        root.put("gridfsConnectionUp", mongoUp);
        out().println(jsonMapper().writeValueAsString(root));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise storage status to JSON: " + e.getMessage(), e);
      }
      return mongoUp ? 0 : 1;
    }

    out().println("STORAGE — file-payload adapter status");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    // FS1a placeholder for the active-provider field. FS1d wires
    // the real admin endpoint; until then the CLI cannot read the
    // backend's deploy-time choice. Operators consulting
    // `shepard-admin storage status` see the hint + the connection
    // signal that actually lives in `/healthz/ready` already.
    table.addRow("active provider", "(FS1d will expose this; check shepard.storage.provider)");
    table.addRow("gridfs connection", mongoStatus);
    out().print(table.render());
    out().println();
    out().println("note: full provider listing + per-adapter detail lands in FS1d");
    out().println("      (GET /v2/admin/storage); FS1b adds the S3 adapter under plugins/storage-s3/.");

    return mongoUp ? 0 : 1;
  }

  private static HealthCheckResult.Check findMongoCheck(HealthCheckResult result) {
    if (result == null || result.getChecks() == null) return null;
    for (HealthCheckResult.Check c : result.getChecks()) {
      if (c.getName() != null && c.getName().toLowerCase().contains("mongo")) {
        return c;
      }
    }
    return null;
  }
}
