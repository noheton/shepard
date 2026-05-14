package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3Config;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin storage s3 status} — FS1b.
 *
 * <p>Fetches and prints the runtime {@code :S3StorageConfig} singleton.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current S3 storage runtime config."
)
public final class S3StatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    S3Config cfg = buildClient().getJson(S3AdminPaths.CONFIG, new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(cfg.isEnabled()));
    table.addRow("endpointUrl", nv(cfg.getEndpointUrl()));
    table.addRow("region", nv(cfg.getRegion()));
    table.addRow("bucket", nv(cfg.getBucket()));
    table.addRow("bucketPrefix", nv(cfg.getBucketPrefix()));
    table.addRow("forcePathStyle", Boolean.toString(cfg.isForcePathStyle()));
    table.addRow("accessKeyId", nv(cfg.getAccessKeyId()));
    table.addRow("secretKeySet", Boolean.toString(cfg.isSecretKeySet()));
    table.addRow(
      "secretKey.fingerprint",
      cfg.getSecretKeyFingerprint() == null ? "(no credential)" : cfg.getSecretKeyFingerprint()
    );
    table.addRow("sseAlgorithm", nv(cfg.getSseAlgorithm()));
    table.addRow("multipartThresholdBytes", Long.toString(cfg.getMultipartThresholdBytes()));
    table.addRow("connectionTimeoutSeconds", Integer.toString(cfg.getConnectionTimeoutSeconds()));
    table.addRow("requestTimeoutSeconds", Integer.toString(cfg.getRequestTimeoutSeconds()));
    table.addRow("updatedAt", cfg.getUpdatedAt() == null ? "(never)" : cfg.getUpdatedAt().toString());
    table.addRow("updatedBy", nv(cfg.getUpdatedBy()));
    out().print(table.render());
    return 0;
  }

  private static String nv(String s) {
    return (s == null || s.isBlank()) ? "-" : s;
  }
}
