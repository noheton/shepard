package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3Config;
import java.io.IOException;
import java.net.http.HttpResponse;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin storage s3 clear-credentials} — FS1b.
 *
 * <p>Wipes the stored S3 credential (accessKeyId +
 * secretAccessKeyCipher + secretAccessKeyHash) via
 * {@code DELETE /v2/admin/storage/s3/credential}. Subsequent
 * put/get calls throw {@code storage.provider.unavailable} until
 * fresh credentials are set.
 */
@Command(
  name = "clear-credentials",
  mixinStandardHelpOptions = true,
  description = "Clear the stored S3 credential. Future put/get calls will fail until re-set."
)
public final class S3ClearCredentialsCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    HttpResponse<String> response = buildClient().delete(S3AdminPaths.CREDENTIAL);
    String body = response.body() == null ? "{}" : response.body();
    S3Config cfg;
    try {
      cfg = new ObjectMapper().readValue(body.isBlank() ? "{}" : body, S3Config.class);
    } catch (IOException e) {
      throw new AdminCliException("Could not parse clear-credentials response: " + e.getMessage(), e);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "S3 credential cleared. secretKeySet=" + cfg.isSecretKeySet() +
      ". Future put/get calls will fail until set-credentials is run again."
    );
    return 0;
  }
}
