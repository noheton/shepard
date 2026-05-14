package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3TestConnection;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin storage s3 test-connection} — FS1b.
 *
 * <p>Issues a {@code POST /v2/admin/storage/s3/test-connection}
 * which has the server probe the configured bucket via a
 * {@code HeadBucketRequest} and reports
 * {@code reachable / statusCode / latencyMs}. Useful before
 * flipping {@code enabled=true}.
 *
 * <p>Exit code: 0 on reachable, 1 on unreachable (so CI scripts can
 * fail fast on a misconfigured deployment).
 */
@Command(
  name = "test-connection",
  mixinStandardHelpOptions = true,
  description = "Probe the configured S3 endpoint + bucket. Exits 0 on reachable, 1 on unreachable."
)
public final class S3TestConnectionCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    S3TestConnection result = buildClient()
      .postJson(S3AdminPaths.TEST_CONNECTION, null, new TypeReference<S3TestConnection>() {});

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
    table.addRow("endpoint", result.getEndpoint() == null ? "(unset)" : result.getEndpoint());
    table.addRow("bucket", result.getBucket() == null ? "(unset)" : result.getBucket());
    if (result.getDetail() != null) {
      table.addRow("detail", result.getDetail());
    }
    out().print(table.render());
    return result.isReachable() ? 0 : 1;
  }
}
