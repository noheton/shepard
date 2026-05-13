package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import java.io.IOException;
import java.net.http.HttpResponse;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite clear-password} — KIP1d.
 *
 * <p>Wipes {@code :DataciteMinterConfig.passwordCipher} +
 * {@code passwordHash} via
 * {@code DELETE /v2/admin/minters/datacite/credential}. Subsequent
 * mint calls throw {@code publish.minter.failed} until a fresh
 * credential is set.
 */
@Command(
  name = "clear-password",
  mixinStandardHelpOptions = true,
  description = "Clear the stored DataCite credential. Future mint calls will fail until re-set."
)
public final class DataciteClearPasswordCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    HttpResponse<String> response = buildClient().delete(DataciteAdminPaths.CREDENTIAL);
    String body = response.body() == null ? "{}" : response.body();
    DataciteConfig cfg;
    try {
      cfg = new ObjectMapper().readValue(body.isBlank() ? "{}" : body, DataciteConfig.class);
    } catch (IOException e) {
      throw new AdminCliException("Could not parse clear-password response: " + e.getMessage(), e);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "DataCite credential cleared. passwordSet=" + cfg.isPasswordSet() +
      ". Future mint calls will fail until set-password is run again."
    );
    return 0;
  }
}
