package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import java.io.IOException;
import java.net.http.HttpResponse;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters epic clear-credential} — KIP1c.
 *
 * <p>Wipes {@code :EpicMinterConfig.credentialKey} +
 * {@code credentialHash} via
 * {@code DELETE /v2/admin/minters/epic/credential}. Subsequent
 * mint calls throw {@code publish.minter.failed} until a fresh
 * credential is set.
 */
@Command(
  name = "clear-credential",
  mixinStandardHelpOptions = true,
  description = "Clear the stored ePIC credential. Future mint calls will fail until re-set."
)
public final class EpicClearCredentialCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    HttpResponse<String> response = buildClient().delete(EpicAdminPaths.CREDENTIAL);
    String body = response.body() == null ? "{}" : response.body();
    EpicConfig cfg;
    try {
      cfg = new ObjectMapper().readValue(body.isBlank() ? "{}" : body, EpicConfig.class);
    } catch (IOException e) {
      throw new AdminCliException("Could not parse clear-credential response: " + e.getMessage(), e);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "ePIC credential cleared. credentialSet=" + cfg.isCredentialSet() +
      ". Future mint calls will fail until set-credential is run again."
    );
    return 0;
  }
}
